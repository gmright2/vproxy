package vproxyx.websocks;

import vproxy.http.HttpContext;
import vproxy.http.HttpProtocolHandler;
import vproxy.processor.http1.entity.Header;
import vproxy.processor.http1.entity.Request;
import vproxy.processor.http1.entity.Response;
import vproxy.protocol.ProtocolHandlerContext;
import vproxy.util.ByteArray;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.util.Date;
import java.util.LinkedList;

public class RedirectHandler extends HttpProtocolHandler {
    private final String protocol;
    private final String domain;
    private final int port;

    public RedirectHandler(String protocol, String domain, int port) {
        super(false);
        this.protocol = protocol;
        this.domain = domain;
        this.port = port;
    }

    private Response buildResponse(Request req) {
        Response resp = new Response();
        resp.version = "HTTP/1.1";
        resp.statusCode = 301;
        resp.reason = "Moved Permanently";
        resp.headers = new LinkedList<>();

        ByteArray body = ByteArray.from(("" +
            "<html>\r\n" +
            "<head><title>301 Moved Permanently</title></head>\r\n" +
            "<body bgcolor=\"white\">\r\n" +
            "<center><h1>301 Moved Permanently</h1></center>\r\n" +
            "<hr><center>nginx/1.14.2</center>\r\n" +
            "</body>\r\n" +
            "</html>\r\n").getBytes());

        resp.headers.add(new Header("Server", "nginx/1.14.2"));
        resp.headers.add(new Header("Date", new Date().toString()));
        resp.headers.add(new Header("Content-Type", "text/html"));
        resp.headers.add(new Header("Content-Length", "" + body.length()));
        resp.headers.add(new Header("Connection", "keep-alive"));
        resp.body = body;

        String domain = this.domain;
        if (domain == null) {
            // extract domain from request
            if (req.headers != null) {
                for (Header h : req.headers) {
                    if (h.key.trim().equalsIgnoreCase("host")) {
                        domain = h.value.trim();
                        if (domain.contains(":")) {
                            domain = domain.substring(0, domain.indexOf(":"));
                        }
                        break;
                    }
                }
            }
        }
        if (domain == null) {
            // still null
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "domain not specified and request does not contain Host header");
        }

        String location;
        {
            if ((protocol.equals("https") && port == 443) || (protocol.equals("http") && port == 80)) {
                location = protocol + "://" + domain;
            } else {
                location = protocol + "://" + domain + ":" + port;
            }
        }
        resp.headers.add(new Header("Location", location));

        return resp;
    }

    @Override
    protected void request(ProtocolHandlerContext<HttpContext> ctx) {
        ctx.write(buildResponse(ctx.data.result).toByteArray().toJavaArray());
    }
}
