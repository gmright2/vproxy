package vproxy.util.ringbuffer;

import vproxy.util.Logger;
import vproxy.util.RingBuffer;
import vproxy.util.RingBufferETHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Deque;
import java.util.LinkedList;

public abstract class AbstractUnwrapRingBuffer extends AbstractRingBuffer {
    class WritableHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            triggerReadable(); // proxy the event
        }

        @Override
        public void writableET() {
            generalUnwrap();
        }
    }

    private static final int MAX_INTERMEDIATE_BUFFER_CAPACITY = 1024 * 1024; // 1M

    private /*might be replaced when switching*/ ByteBufferRingBuffer plainBufferForApp;
    protected final SimpleRingBuffer encryptedBufferForInput;
    protected final WritableHandler writableHandler = new WritableHandler();
    private final Deque<ByteBufferRingBuffer> intermediateBuffers = new LinkedList<>();
    private ByteBuffer temporaryBuffer = null;

    private boolean triggerWritable = false;

    public AbstractUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp) {
        this.plainBufferForApp = plainBufferForApp;

        // we add a handler to the plain buffer
        plainBufferForApp.addHandler(writableHandler);

        this.encryptedBufferForInput = RingBuffer.allocateDirect(plainBufferForApp.capacity());
    }

    @Override
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        int read = encryptedBufferForInput.storeBytesFrom(channel);
        if (read == 0) {
            return 0; // maybe the buffer is full
        }
        if (read == -1) {
            assert Logger.lowLevelDebug("reading from remote return -1");
            return -1;
        }
        // got new data, let's unwrap it
        generalUnwrap();
        return read;
    }

    // -------------------
    // helper functions BEGIN
    // -------------------
    protected void recordIntermediateBuffer(ByteBuffer b) {
        intermediateBuffers.add(SimpleRingBuffer.wrap(b));
    }

    private int intermediateBufferCap() {
        int cap = 0;
        for (ByteBufferRingBuffer buf : intermediateBuffers) {
            cap += buf.capacity();
        }
        return cap;
    }

    protected ByteBuffer getTemporaryBuffer(int cap) {
        if (temporaryBuffer != null && temporaryBuffer.capacity() >= cap) {
            temporaryBuffer.limit(temporaryBuffer.capacity()).position(0);
            return temporaryBuffer;
        }
        temporaryBuffer = ByteBuffer.allocate(cap);
        return temporaryBuffer;
    }

    protected void discardTemporaryBuffer() {
        temporaryBuffer = null;
    }
    // -------------------
    // helper functions END
    // -------------------

    protected void generalUnwrap() {
        if (isOperating()) {
            return; // should not call the method when it's operating
        }
        setOperating(true);
        try {
            _generalUnwrap();
        } finally {
            if (triggerWritable) {
                triggerWritable = false;
                triggerWritable();
            }
            setOperating(false);
        }
    }

    private void _generalUnwrap() {
        if ((intermediateBuffers.isEmpty() || plainBufferForApp.free() == 0)
            &&
            (encryptedBufferForInput.used() == 0 || intermediateBufferCap() > MAX_INTERMEDIATE_BUFFER_CAPACITY)) {
            return;
        }

        // check the intermediate buffers
        while (!intermediateBuffers.isEmpty()) {
            ByteBufferRingBuffer buf = intermediateBuffers.peekFirst();
            int wrote = 0;
            if (buf.used() != 0) {
                wrote = buf.writeTo(plainBufferForApp, Integer.MAX_VALUE);
            }
            assert Logger.lowLevelDebug("wrote " + wrote + " bytes to plain buffer");
            // remove the buffer if all data wrote
            if (buf.used() == 0) {
                intermediateBuffers.pollFirst();
                triggerWritable = true;
            }
            // break the process if no space for app buffer
            if (plainBufferForApp.free() == 0) {
                break;
            }
        }

        // then check the input encrypted buffer
        //noinspection ConstantConditions
        do {
            try {
                if (encryptedBufferForInput.used() == 0) {
                    break;
                }
                // check the intermediate capacity
                if (intermediateBufferCap() > MAX_INTERMEDIATE_BUFFER_CAPACITY) {
                    break; // should not run the operation when capacity reaches the limit
                }
                boolean canDefragment = encryptedBufferForInput.canDefragment();
                boolean[] underflow = {false};
                boolean[] errored = {false};
                encryptedBufferForInput.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                    encryptedBuffer -> handleEncryptedBuffer(encryptedBuffer, underflow, errored));
                if (underflow[0]) {
                    if (canDefragment) {
                        encryptedBufferForInput.defragment();
                    } else {
                        assert Logger.lowLevelDebug("got underflow, but the encrypted buffer cannot defragment, maybe buffer limit to small, or data not enough yet");
                        errored[0] = true;
                    }
                }
                if (errored[0]) {
                    return; // exit if error occurred
                }
            } catch (IOException e) {
                // it's memory operation, should not happen
                Logger.shouldNotHappen("got exception when unwrapping", e);
            }
        } while (false); // use do-while to implement goto

        // finally recursively call the method to make sure everything is done
        _generalUnwrap();
    }

    abstract protected void handleEncryptedBuffer(ByteBuffer buf, boolean[] underflow, boolean[] errored);

    @Override
    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        // proxy the operation from plain buffer
        return plainBufferForApp.writeTo(channel, maxBytesToWrite);
    }

    @Override
    public int free() {
        // whether have space to store data is determined by network input buffer
        return encryptedBufferForInput.free();
    }

    @Override
    public int used() {
        // user may use this to check whether the buffer still had data left
        return plainBufferForApp.used();
    }

    @Override
    public int capacity() {
        // capacity of the plain buffer and encrypted buffer are the same
        return plainBufferForApp.capacity();
    }

    @Override
    public void clean() {
        plainBufferForApp.clean();
        encryptedBufferForInput.clean();
    }

    @Override
    public void clear() {
        plainBufferForApp.clear();
    }

    @Override
    public RingBuffer switchBuffer(RingBuffer buf) throws RejectSwitchException {
        if (plainBufferForApp.used() != 0)
            throw new RejectSwitchException("the plain buffer is not empty");
        if (!(buf instanceof ByteBufferRingBuffer))
            throw new RejectSwitchException("the input is not a ByteBufferRingBuffer");
        if (buf.capacity() != plainBufferForApp.capacity())
            throw new RejectSwitchException("capacity of new buffer is not the same as the old one");

        // switch buffers and handlers
        plainBufferForApp.removeHandler(writableHandler);
        plainBufferForApp = (ByteBufferRingBuffer) buf;
        plainBufferForApp.addHandler(writableHandler);

        // try to unwrap any data if presents
        generalUnwrap();

        return this;
    }
}