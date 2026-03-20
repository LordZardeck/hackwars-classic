package util;

import java.util.HashMap;

public final class LegacyRemoteDefaults {
    private LegacyRemoteDefaults() {
    }

    public static String normalizeDomain(String input) {
        if (input == null) {
            return "";
        }

        String value = input.trim();
        if (value.length() == 0) {
            return "";
        }

        try {
            if (value.indexOf("://") != -1) {
                java.net.URL parsed = new java.net.URL(value);
                if (parsed.getHost() != null && parsed.getHost().length() > 0) {
                    return parsed.getHost().toLowerCase();
                }
            }
        } catch (Exception ignored) {
        }

        if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        } else if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        }

        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        return value.toLowerCase();
    }

    public static HashMap<String, Object> compileApplicationResult() {
        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("error", "");
        result.put("price", Double.valueOf(0.0));
        result.put("cpucost", Double.valueOf(0.0));
        return result;
    }

    public static String unavailableHtml(String title, String body) {
        return "<html><body><h2>" + title + "</h2><p>" + body + "</p></body></html>";
    }
}
