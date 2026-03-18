package util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayFabTokenVerifier {
    private static final String TITLE_ID_PROPERTY = "hackwars.playfab.titleId";
    private static final String BASE_URL_PROPERTY = "hackwars.playfab.baseUrl";
    private static final String SECRET_KEY_PROPERTY = "hackwars.playfab.secretKey";
    private static final String TIMEOUT_SECONDS_PROPERTY = "hackwars.playfab.timeoutSeconds";
    private static final String DEFAULT_TITLE_ID = "1EAAB9";
    private static final long DEFAULT_TIMEOUT_SECONDS = 12L;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private PlayFabTokenVerifier() {
    }

    public static final class AuthResult {
        private final String playFabId;
        private final String userName;
        private final String playerIp;

        public AuthResult(String playFabId, String userName, String playerIp) {
            this.playFabId = playFabId;
            this.userName = userName;
            this.playerIp = playerIp;
        }

        public String getPlayFabId() {
            return playFabId;
        }

        public String getUserName() {
            return userName;
        }

        public String getPlayerIp() {
            return playerIp;
        }
    }

    public static AuthResult verify(String accessToken) throws Exception {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing PlayFab access token.");
        }

        String secretKey = getProperty(SECRET_KEY_PROPERTY, "");
        if (secretKey.isEmpty()) {
            throw new IllegalStateException("Missing PlayFab secret key property: " + SECRET_KEY_PROPERTY);
        }

        String authResponse = authenticateSessionTicket(accessToken, secretKey);
        if ("true".equalsIgnoreCase(extractJsonBoolean(authResponse, "IsSessionTicketExpired"))) {
            throw new IllegalStateException("PlayFab session ticket is expired.");
        }

        String playFabId = extractJsonString(authResponse, "PlayFabId");
        if (playFabId == null || playFabId.trim().isEmpty()) {
            throw new IllegalStateException("PlayFab authentication response did not include PlayFabId.");
        }

        String userName = extractJsonString(authResponse, "Username");
        if (userName == null || userName.trim().isEmpty()) {
            userName = extractJsonString(authResponse, "DisplayName");
        }
        if (userName == null || userName.trim().isEmpty()) {
            userName = playFabId;
        }

        String userDataResponse = getUserData(playFabId, secretKey);
        String playerIp = extractPlayerIp(userDataResponse);
        if (playerIp == null || playerIp.trim().isEmpty()) {
            throw new IllegalStateException("PlayFab user data does not contain player_ip.");
        }

        return new AuthResult(playFabId, userName, playerIp.trim());
    }

    private static String authenticateSessionTicket(String accessToken, String secretKey) throws Exception {
        String body = "{\"SessionTicket\":\"" + escapeJson(accessToken) + "\"}";
        return post("/Server/AuthenticateSessionTicket", body, secretKey);
    }

    private static String getUserData(String playFabId, String secretKey) throws Exception {
        String body = "{\"PlayFabId\":\"" + escapeJson(playFabId) + "\",\"Keys\":[\"player_ip\"]}";
        return post("/Server/GetUserReadOnlyData", body, secretKey);
    }

    private static String post(String path, String body, String secretKey) throws Exception {
        String titleId = getProperty(TITLE_ID_PROPERTY, DEFAULT_TITLE_ID);
        String baseUrl = getProperty(BASE_URL_PROPERTY, "https://" + titleId + ".playfabapi.com");
        long timeoutSeconds = parseLong(getProperty(TIMEOUT_SECONDS_PROPERTY, String.valueOf(DEFAULT_TIMEOUT_SECONDS)), DEFAULT_TIMEOUT_SECONDS);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + path))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("X-SecretKey", secretKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            String errorMessage = extractJsonString(response.body(), "errorMessage");
            if (errorMessage == null || errorMessage.length() == 0) {
                errorMessage = "PlayFab returned HTTP " + response.statusCode() + ".";
            }
            throw new IllegalStateException(errorMessage);
        }
        return response.body();
    }

    private static String extractPlayerIp(String json) {
        Pattern keyPattern = Pattern.compile("\"player_ip\"\\s*:\\s*\\{", Pattern.CASE_INSENSITIVE);
        Matcher keyMatcher = keyPattern.matcher(json);
        if (!keyMatcher.find()) {
            return null;
        }
        String tail = json.substring(keyMatcher.start());
        return extractJsonString(tail, "Value");
    }

    private static String extractJsonString(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJson(matcher.group(1));
    }

    private static String extractJsonBoolean(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') sb.append("\\\\");
            else if (ch == '"') sb.append("\\\"");
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else if (ch == '\t') sb.append("\\t");
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static String getProperty(String key, String fallback) {
        try {
            String value = System.getProperty(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
