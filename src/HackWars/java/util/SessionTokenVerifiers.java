package util;

public final class SessionTokenVerifiers {
    private static final SessionTokenVerifier PLAYFAB_BACKED = new SessionTokenVerifier() {
        @Override
        public PlayFabTokenVerifier.AuthResult verify(String accessToken) throws Exception {
            return PlayFabTokenVerifier.verify(accessToken);
        }
    };

    private static volatile SessionTokenVerifier overrideVerifier;

    private SessionTokenVerifiers() {
    }

    public static SessionTokenVerifier active() {
        return overrideVerifier != null ? overrideVerifier : PLAYFAB_BACKED;
    }

    public static void install(SessionTokenVerifier verifier) {
        overrideVerifier = verifier;
    }

    public static void reset() {
        overrideVerifier = null;
    }
}
