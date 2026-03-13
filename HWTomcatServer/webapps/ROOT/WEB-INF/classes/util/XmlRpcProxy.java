package util;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import java.net.URL;

public class XmlRpcProxy {

    public static Object execute(String url, String method, Object[] send) {
        Object result = null;
        try {
            Endpoint endpoint = rewriteEndpoint(url, method);
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(endpoint.url));
            XmlRpcClient client = new XmlRpcClient();
            client.setConfig(config);
            result = client.execute(endpoint.method, send);
        } catch (Exception e) {
            e.printStackTrace();
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
                        port = Integer.parseInt(getPropertySafe("hackwars.server.port", "8081"));
                    } catch (Exception e) {
                        port = 8081;
                    }
                }
                String protocol = parsed.getProtocol();
                if (protocol == null || protocol.equals("")) {
                    protocol = "http";
                }
                endpoint.url = protocol + "://" + host + ":" + port + "/xmlrpc";
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

    private static class Endpoint {
        String url;
        String method;

        Endpoint(String url, String method) {
            this.url = url;
            this.method = method;
        }
    }
}
