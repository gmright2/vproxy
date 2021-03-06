package vproxy.app;

import vfd.FDProvider;
import vproxy.component.exception.NotFoundException;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.dns.Cache;
import vproxy.dns.ResolveListener;
import vproxy.dns.Resolver;
import vproxy.util.Callback;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Tuple;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ServerAddressUpdater implements ResolveListener {
    private static ServerAddressUpdater updater = new ServerAddressUpdater();

    private ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    private ServerAddressUpdater() {
    }

    public static void init() {
        Resolver resolver = Resolver.getDefault();
        resolver.addListener(updater);
    }

    @Override
    public void onResolve(Cache cache) {
        Cache old = cacheMap.put(cache.host, cache);
        if (old == null) {
            // nothing to handle
            return;
        }
        handle(cache, old);
    }

    private static void handle(Cache newCache, Cache oldCache) {
        long cur = FDProvider.get().currentTimeMillis();
        if (cur - oldCache.timestamp > 3600_000) { // scan the whole list every one hour
            checkAll(newCache.host, newCache);
        } else {
            checkMissing(newCache.host, newCache, oldCache);
        }
    }

    private static void checkAll(String host, Cache c) {
        Set<InetAddress> addresses = new HashSet<>();
        addresses.addAll(c.ipv4);
        addresses.addAll(c.ipv6);

        List<String> groupNames = Application.get().serverGroupHolder.names();

        for (String groupName : groupNames) {
            ServerGroup grp;
            try {
                grp = Application.get().serverGroupHolder.get(groupName);
            } catch (NotFoundException ignore) {
                // ignore if it's deleted
                continue;
            }

            List<ServerGroup.ServerHandle> handles = grp.getServerHandles();
            for (ServerGroup.ServerHandle h : handles) {
                // host name matches, and the address is not in new record
                if (host.equals(/*may be null*/h.hostName) && !addresses.contains(h.server.getAddress())) {
                    doReplace(grp, c, h);
                }
            }
        }
    }

    private static void checkMissing(String host, Cache newCache, Cache oldCache) {
        Set<InetAddress> missing = new HashSet<>();
        for (Inet4Address n : oldCache.ipv4) {
            if (!newCache.ipv4.contains(n)) {
                // in old, but not in new
                missing.add(n);
            }
        }
        for (Inet6Address n : oldCache.ipv6) {
            if (!newCache.ipv6.contains(n)) {
                // in old, but not in new
                missing.add(n);
            }
        }
        if (!missing.isEmpty()) {
            handleMissing(host, newCache, missing);
        }
    }

    private static void handleMissing(String host, Cache c, Set<InetAddress> missing) {
        List<String> groupNames = Application.get().serverGroupHolder.names();

        for (String groupName : groupNames) {
            ServerGroup grp;
            try {
                grp = Application.get().serverGroupHolder.get(groupName);
            } catch (NotFoundException ignore) {
                // ignore if it's deleted
                continue;
            }

            List<ServerGroup.ServerHandle> handles = grp.getServerHandles();
            for (ServerGroup.ServerHandle h : handles) {
                // host name matches, and the address is missing
                if (host.equals(/*may be null*/h.hostName) && missing.contains(h.server.getAddress())) {
                    doReplace(grp, c, h);
                }
            }
        }
    }

    private static void doReplace(ServerGroup grp, Cache c, ServerGroup.ServerHandle h) {
        Tuple<Inet4Address, Inet6Address> tup = c.next();
        if (h.server.getAddress() instanceof Inet4Address) {
            if (tup.left != null) {
                Logger.info(LogType.RESOLVE_REPLACE,
                    "replace grp=" + grp.alias +
                        ", server=" + h.alias +
                        ", old=" + h.server.getAddress() +
                        ", new=" + tup.left);
                try {
                    grp.replaceIp(h.alias, tup.left);
                } catch (NotFoundException ignore) {
                    // ignore if it's deleted
                }
            }
        } else if (h.server.getAddress() instanceof Inet6Address) {
            if (tup.right != null) {
                Logger.info(LogType.RESOLVE_REPLACE,
                    "replace grp=" + grp.alias +
                        ", server=" + h.alias +
                        ", old=" + h.server.getAddress() +
                        ", new=" + tup.right);
                try {
                    grp.replaceIp(h.alias, tup.right);
                } catch (NotFoundException ignore) {
                    // ignore if it's deleted
                }
            }
        } // else should not happen, we ignore
    }

    @Override
    public void onRemove(Cache cache) {
        // re-resolve it

        String host = cache.host;
        Resolver.getDefault().resolve(host, new Callback<>() {
            @Override
            protected void onSucceeded(InetAddress value) {
                // ignore
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                // ignore
            }
        });
    }
}
