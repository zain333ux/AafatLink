package pk.edu.nu.aafatlink.domain.auth;

import java.util.Objects;
import java.util.UUID;

/** Result of creating an Auth identity; application role onboarding happens separately. */
public record RegistrationResult(UUID userId, String email, boolean emailConfirmationRequired) {
    public RegistrationResult {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
    }
}
