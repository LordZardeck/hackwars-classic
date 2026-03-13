package util;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import java.net.URL;

public class XmlRpcProxy {

    public static Object execute(String url, String method, Object[] send) {
        Object result = null;
        Endpoint endpoint = rewriteEndpoint(url, method);
        try {
            result = executeRequest(endpoint.url, endpoint.method, send);
        } catch (Exception firstError) {
            String fallbackUrl = alternateXmlRpcUrl(endpoint.url);
            if (fallbackUrl != null && !fallbackUrl.equals(endpoint.url)) {
                try {
                    result = executeRequest(fallbackUrl, endpoint.method, send);
                } catch (Exception secondError) {
                    secondError.printStackTrace();
                }
            } else {
                firstError.printStackTrace();
            }
        }
        return result;
    }

    private static Endpoint rewriteEndpoint(String url, String method) {
        Endpoint endpoint = new Endpoint(url, method);
        if (url == null) {
            return endpoint;
        }
        try {
            URL parsed = new URL(url);
            String path = parsed.getPath();
            if (path != null && path.startsWith("/xmlrpc/") && path.endsWith(".php")) {
                String endpointName = path.substring("/xmlrpc/".length(), path.length() - ".php".length());
                String host = parsed.getHost();
                if (host == null || host.equals("") || host.equalsIgnoreCase("hackwars.net") || host.equalsIgnoreCase("www.hackwars.net")) {
                    host = getPropertySafe("hackwars.server.host", "127.0.0.1");
                }
                int port = parsed.getPort();
                if (port <= 0) {
                    try {
                        port = Integer.parseInt(getPropertySafe("hackwars.server.port", "8080"));
                    } catch (Exception e) {
                        port = 8080;
                    }
                }
                String protocol = parsed.getProtocol();
                if (protocol == null || protocol.equals("")) {
                    protocol = "http";
                }
                String contextPath = normalizeContextPath(getPropertySafe("hackwars.server.context", "hackwars"));
                endpoint.url = protocol + "://" + host + ":" + port + contextPath + "/xmlrpc";
                if (method != null && method.indexOf('.') == -1) {
                    endpoint.method = endpointName + "." + method;
                }
            }
        } catch (Exception e) {
        }
        return endpoint;
    }

    private static String getPropertySafe(String key, String fallback) {
        try {
            return System.getProperty(key, fallback);
        } catch (SecurityException e) {
            return fallback;
        }
    }

    private static String normalizeContextPath(String contextPath) {
        String path = contextPath == null ? "" : contextPath.trim();
        if (path.equals("") || path.equals("/")) {
            return "";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static Object executeRequest(String endpointUrl, String method, Object[] send) throws Exception {
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(endpointUrl));
        XmlRpcClient client = new XmlRpcClient();
        client.setConfig(config);
        return client.execute(method, send);
    }

    private static String alternateXmlRpcUrl(String endpointUrl) {
        if (endpointUrl == null) {
            return null;
        }
        try {
            URL parsed = new URL(endpointUrl);
            String contextPath = normalizeContextPath(getPropertySafe("hackwars.server.context", "hackwars"));
            String path = parsed.getPath();
            String targetPath = null;
            if ("/xmlrpc".equals(path) && !contextPath.equals("")) {
                targetPath = contextPath + "/xmlrpc";
            } else if (!contextPath.equals("") && (contextPath + "/xmlrpc").equals(path)) {
                targetPath = "/xmlrpc";
            }
            if (targetPath == null) {
                return null;
            }
            int port = parsed.getPort();
            String host = parsed.getHost();
            String protocol = parsed.getProtocol();
            if (port > 0) {
                return protocol + "://" + host + ":" + port + targetPath;
            }
            return protocol + "://" + host + targetPath;
        } catch (Exception e) {
            return null;
        }
    }

    private static class Endpoint {
        String url;
        String method;

        Endpoint(String url, String method) {
            this.url = url;
            this.method = method;
        }
    }
}
