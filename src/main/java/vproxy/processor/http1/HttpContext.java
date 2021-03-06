package vproxy.processor.http1;

import vproxy.processor.Hint;
import vproxy.processor.OOContext;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.net.InetSocketAddress;

public class HttpContext extends OOContext<HttpSubContext> {
    final String clientAddress;
    final String clientPort;

    int currentBackend = -1;

    private boolean hintExists = false;
    private Hint hint;

    public HttpContext(InetSocketAddress clientSock) {
        clientAddress = clientSock == null ? null : Utils.ipStr(clientSock.getAddress().getAddress());
        clientPort = clientSock == null ? null : "" + clientSock.getPort();
    }

    @Override
    public int connection(HttpSubContext front) {
        if (!front.hostHeaderRetrieved) {
            return 0; // do not send data for now
        }
        if (front.isIdle()) {
            // the state may turn to idle after calling feed()
            // the connection() will be called after calling feed()
            // so here we should return the last recorded backend id
            // then set the id to -1
            int foo = currentBackend;
            currentBackend = -1;
            return foo;
        }
        return currentBackend;
    }

    @Override
    public Hint connectionHint(HttpSubContext front) {
        if (hintExists) {
            return hint;
        }
        String host = front.theHostHeader;
        if (host == null) {
            return null;
        }
        assert Logger.lowLevelDebug("got Host from front sub context: " + host);
        if (host.contains(":")) { // remove port in Host header
            host = host.substring(0, host.lastIndexOf(":"));
        }
        if (Utils.isIpLiteral(host)) {
            hintExists = true;
            return null; // no hint if requesting directly using ip
        }
        if (host.startsWith("www.")) { // remove www. convention
            host = host.substring("www.".length());
        }
        hintExists = true;
        hint = new Hint(host);
        return hint;
    }

    @Override
    public void chosen(HttpSubContext front, HttpSubContext subCtx) {
        currentBackend = subCtx.connId;
    }
}
