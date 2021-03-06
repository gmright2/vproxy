package vproxyx.websocks;

import vproxy.util.ByteArray;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class WebRootPageProvider implements PageProvider {
    private final String baseDir;
    private final String protocol;
    private final String domain;
    private final int port;
    private final ConcurrentHashMap<String, Page> pages = new ConcurrentHashMap<>();

    private static class Page {
        final ByteArray content;
        final long updateTime;

        private Page(ByteArray content, long updateTime) {
            this.content = content;
            this.updateTime = updateTime;
        }
    }

    public WebRootPageProvider(String baseDir, RedirectBaseInfo info) {
        this.baseDir = baseDir;
        this.protocol = info.protocol;
        this.domain = info.domain;
        this.port = info.port;
    }

    private File findFile(String url) {
        // try the url
        Path p = Path.of(baseDir, url);
        File f = p.toFile();
        if (f.exists() && !f.isDirectory()) {
            return f;
        }
        // try to add index.html
        p = Path.of(baseDir, url, "index.html");
        f = p.toFile();
        if (f.exists() && !f.isDirectory()) {
            return f;
        }
        // for url not ending with '/'
        // append '.html'
        if (!url.endsWith("/")) {
            p = Path.of(baseDir, url + ".html");
            f = p.toFile();
            if (f.exists() && !f.isDirectory()) {
                return f;
            }
        }
        // not found
        return null;
    }

    private String getMime(File f) {
        String name = f.getName();
        if (name.endsWith(".html")) {
            return "text/html";
        } else if (name.endsWith(".js")) {
            return "text/javascript";
        } else if (name.endsWith(".css")) {
            return "text/css";
        } else if (name.endsWith(".json")) {
            return "application/json";
        } else {
            return "application/octet-stream";
        }
    }

    @Override
    public PageResult getPage(String url) {
        if (!url.endsWith("/")) {
            // maybe url not ending with `/` but it's a directory
            // relative path may be wrong
            File file = Path.of(baseDir, url).toFile();
            if (file.isDirectory()) {
                // need to redirect
                String portStr = ":" + port;
                if (protocol.equals("http")) {
                    if (port == 80) {
                        portStr = "";
                    }
                } else if (protocol.equals("https")) {
                    if (port == 443) {
                        portStr = "";
                    }
                }
                return new PageResult(protocol + "://" + domain + portStr + url + "/");
            }
        }
        File file = findFile(url);
        if (file == null) {
            return null;
        }
        String mime = getMime(file);
        String key = file.getAbsolutePath();
        if (!file.exists() || file.isDirectory()) {
            pages.remove(key);
            return null;
        }

        long time = file.lastModified();
        Page page = pages.get(key);
        if (page != null) {
            if (time != page.updateTime) {
                pages.remove(key);
                page = null;
            }
        }
        if (page != null) {
            assert Logger.lowLevelDebug("using cached page: " + url);
            return new PageResult(mime, page.content);
        }
        assert Logger.lowLevelDebug("reading from disk: " + url);
        byte[] buf = new byte[1024];
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int r;
            while ((r = fis.read(buf)) >= 0) {
                baos.write(buf, 0, r);
            }
            ByteArray content = ByteArray.from(baos.toByteArray());
            page = new Page(content, time);
            pages.put(key, page);
            return new PageResult(mime, page.content);
        } catch (IOException e) {
            Logger.error(LogType.FILE_ERROR, "reading file " + key + " failed", e);
            return null;
        }
    }
}
