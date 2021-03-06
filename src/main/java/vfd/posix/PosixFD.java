package vfd.posix;

import vfd.FD;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class PosixFD implements FD {
    protected final Posix posix;
    private boolean closed = false;
    protected int fd = -1;
    private Boolean blocking = null;
    private Map<SocketOption, Object> opts = new HashMap<>();
    private ByteBuffer directBuffer = null;

    protected PosixFD(Posix posix) {
        this.posix = posix;
    }

    protected ByteBuffer getDirectBuffer(int len) {
        if (directBuffer != null && directBuffer.capacity() < len) {
            Utils.clean(directBuffer);
            directBuffer = null;
        }
        if (directBuffer == null) {
            directBuffer = ByteBuffer.allocateDirect(2 * len);
        }
        return directBuffer;
    }

    protected void resetDirectBuffer() {
        if (directBuffer == null) {
            return;
        }
        directBuffer.limit(directBuffer.capacity()).position(0);
    }

    protected void checkFD() throws IOException {
        if (fd == -1) {
            throw new IOException("connect() not called");
        }
    }

    protected void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        checkNotClosed();
        if (fd == -1) {
            blocking = b;
        } else {
            posix.setBlocking(fd, b);
        }
    }

    protected void finishConfigAfterFDCreated() throws IOException {
        if (blocking != null) {
            configureBlocking(blocking);
        }
        for (var entry : opts.entrySet()) {
            //noinspection unchecked
            setOption(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        checkNotClosed();
        if (fd == -1) {
            if (name != StandardSocketOptions.SO_LINGER
                && name != StandardSocketOptions.SO_REUSEPORT
                && name != StandardSocketOptions.TCP_NODELAY
                && name != StandardSocketOptions.SO_RCVBUF) {
                throw new IOException("not supported " + name);
            }
            opts.put(name, value);
        } else {
            if (name == StandardSocketOptions.SO_LINGER) {
                posix.setSoLinger(fd, (Integer) value);
            } else if (name == StandardSocketOptions.SO_REUSEPORT) {
                posix.setReusePort(fd, (Boolean) value);
            } else if (name == StandardSocketOptions.SO_RCVBUF) {
                posix.setRcvBuf(fd, (Integer) value);
            } else if (name == StandardSocketOptions.TCP_NODELAY) {
                posix.setTcpNoDelay(fd, (Boolean) value);
            } else {
                throw new IOException("not supported " + name);
            }
        }
    }

    @Override
    public FD real() {
        return this;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (fd != -1) {
            posix.close(fd);
        }
        if (directBuffer != null) {
            Utils.clean(directBuffer);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "fd=" + fd +
            ", closed=" + closed +
            '}';
    }
}
