package vproxy.app.cmd.handle.resource;

import vproxy.app.Application;
import vproxy.app.cmd.Command;
import vproxy.app.cmd.Resource;
import vproxy.app.cmd.ResourceType;
import vproxy.component.app.Socks5Server;
import vproxy.component.app.TcpLB;
import vproxy.component.proxy.Session;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class SessionHandle {
    private SessionHandle() {
    }

    public static void checkSession(Resource parent) throws Exception {
        if (parent == null)
            throw new Exception("cannot find " + ResourceType.sess.fullname + " on top level");
        if (parent.type == ResourceType.tl) {
            TcpLBHandle.checkTcpLB(parent);
        } else if (parent.type == ResourceType.socks5) {
            Socks5ServerHandle.checkSocks5Server(parent);
        } else {
            throw new Exception(parent.type.fullname + " does not contain " + ResourceType.sess.fullname);
        }
    }

    public static int count(Resource parent) throws Exception {
        if (parent.type == ResourceType.tl) {
            // get session count from tcp loadbalancer
            TcpLB lb = Application.get().tcpLBHolder.get(parent.alias);
            return lb.sessionCount();
        } else {
            assert parent.type == ResourceType.socks5;
            Socks5Server socks5 = Application.get().socks5ServerHolder.get(parent.alias);
            return socks5.sessionCount();
        }
    }

    public static List<Session> list(Resource parent) throws Exception {
        if (parent.type == ResourceType.tl) {
            TcpLB lb = Application.get().tcpLBHolder.get(parent.alias);

            // retrieve sessions
            List<Session> sessions = new LinkedList<>();
            lb.copySessions(sessions);

            return sessions;
        } else {
            assert parent.type == ResourceType.socks5;
            Socks5Server socks5 = Application.get().socks5ServerHolder.get(parent.alias);

            // retrieve sessions
            List<Session> sessions = new LinkedList<>();
            socks5.copySessions(sessions);

            return sessions;
        }
    }

    public static void close(Command cmd) throws Exception {
        List<Session> sessions = list(cmd.prepositionResource);
        String pattern = cmd.resource.alias;
        Pattern p = null;
        if (pattern.startsWith("/") && pattern.endsWith("/")) {
            p = Pattern.compile(pattern.substring(1, pattern.length() - 1));
        }
        for (Session s : sessions) {
            //noinspection Duplicates
            if (p == null) {
                // directly compare
                if (s.id().equals(pattern)) {
                    s.close();
                    // there can be no other session with the same id
                    break;
                }
            } else {
                // regex test
                if (p.matcher(s.id()).find()) {
                    s.close();
                    // then continue
                }
            }
        }
    }
}
