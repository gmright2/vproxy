package vproxy.component.check;

import vproxy.app.util.AnnotationKeys;

import java.util.Map;

public class AnnotatedHcConfig {
    private String httpMethod;
    private String httpUrl;
    private String httpHost;
    private String httpStatus;

    public void clear() {
        httpMethod = null;
        httpUrl = null;
        httpHost = null;
        httpStatus = null;
    }

    public void set(Map<String, String> map) {
        httpMethod = map.get(AnnotationKeys.ServerGroup_HCHttpMethod);
        httpUrl = map.get(AnnotationKeys.ServerGroup_HCHttpUrl);
        httpHost = map.get(AnnotationKeys.ServerGroup_HCHttpHost);

        String x = map.get(AnnotationKeys.ServerGroup_HCHttpStatus);
        if (x != null) {
            String[] strs = x.split(",");
            for (String s : strs) {
                if (!s.equals("1xx")
                    && !s.equals("2xx")
                    && !s.equals("3xx")
                    && !s.equals("4xx")
                    && !s.equals("5xx")) {
                    x = null;
                    break;
                }
            }
        }
        httpStatus = x;
    }

    public String getHttpMethod() {
        return httpMethod == null ? "GET" : httpMethod;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public String getHttpStatus() {
        return httpStatus == null ? "1xx,2xx,3xx,4xx" : httpStatus;
    }

    public String getHttpUrl() {
        return httpUrl == null ? "/" : httpUrl;
    }
}
