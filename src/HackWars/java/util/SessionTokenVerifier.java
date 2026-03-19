package util;

public interface SessionTokenVerifier {
    PlayFabTokenVerifier.AuthResult verify(String accessToken) throws Exception;
}
