package pk.edu.nu.aafatlink.service.auth;

import java.net.URI;
import java.util.Objects;

/** Parameters used to start a password-recovery PKCE flow. */
public record PasswordRecoveryRequest(String email, URI redirectUri, String codeChallenge) {
    public PasswordRecoveryRequest {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(redirectUri, "redirectUri");
        Objects.requireNonNull(codeChallenge, "codeChallenge");
    }
}
