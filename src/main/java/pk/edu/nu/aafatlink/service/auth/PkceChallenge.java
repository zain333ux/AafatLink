package pk.edu.nu.aafatlink.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** RFC 7636 S256 proof key used by installed-application recovery flows. */
public record PkceChallenge(String codeVerifier, String codeChallenge) {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static PkceChallenge generate() {
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        return fromVerifier(Base64.getUrlEncoder().withoutPadding().encodeToString(entropy));
    }

    public static PkceChallenge fromVerifier(String verifier) {
        if (verifier == null || verifier.length() < 43 || verifier.length() > 128
                || !verifier.matches("[A-Za-z0-9._~-]+")) {
            throw new IllegalArgumentException("PKCE verifier must contain 43 to 128 URL-safe characters.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return new PkceChallenge(verifier, challenge);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("This Java runtime does not support SHA-256.", exception);
        }
    }
}
