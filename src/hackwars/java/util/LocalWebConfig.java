package util;

import java.net.URL;

/**
 * Resolves local web service endpoints used by legacy server code.
 */
public final class LocalWebConfig {
    private static final String BASE_URL = resolveBaseUrl();
    private static final String XMLRPC_URL = BASE_URL + "/xmlrpc";

    private LocalWebConfig() {
    }

    private static String getPropertySafe(String key, String fallback) {
        try {
            return System.getProperty(key, fallback);
        } catch (SecurityException e) {
            return fallback;
        }
    }

    private static String resolveBaseUrl() {
        String configured = getPropertySafe("hackwars.localWebBaseUrl", "").trim();
        if (!configured.equals("")) {
            return stripTrailingSlash(configured);
        }

        String host = getPropertySafe("hackwars.localWebHost", "127.0.0.1");
        String port = getPropertySafe("hackwars.localWebPort", "8080");
        String context = normalizeContext(getPropertySafe("hackwars.localWebContext", "hackwars"));
        return "http://" + host + ":" + port + context;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalizeContext(String context) {
        String result = context == null ? "" : context.trim();
        if (result.equals("") || result.equals("/")) {
            return "";
        }
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public static String getBaseUrl() {
        return BASE_URL;
    }

    public static String getPort() {
        try {
            URL parsed = new URL(BASE_URL);
            int port = parsed.getPort();
            if (port > 0) {
                return Integer.toString(port);
            }
            if ("https".equalsIgnoreCase(parsed.getProtocol())) {
                return "443";
            }
        } catch (Exception e) {
        }
        return "80";
    }

    public static String getBaseUrl(String host) {
        if (host == null || host.trim().equals("")) {
            return BASE_URL;
        }
        String protocol = "http";
        try {
            URL parsed = new URL(BASE_URL);
            if (parsed.getProtocol() != null && !parsed.getProtocol().equals("")) {
                protocol = parsed.getProtocol();
            }
        } catch (Exception e) {
        }
        return protocol + "://" + host + ":" + getPort();
    }

    public static String getXmlRpcUrl() {
        return XMLRPC_URL;
    }

    public static String getXmlRpcUrl(String host) {
        return getBaseUrl(host) + "/xmlrpc";
    }
}
