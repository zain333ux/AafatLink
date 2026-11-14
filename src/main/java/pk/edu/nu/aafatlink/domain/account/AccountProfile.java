package pk.edu.nu.aafatlink.domain.account;

import java.util.Objects;
import java.util.UUID;

/** Role and status loaded from the protected application account table. */
public record AccountProfile(UUID id, String displayName, String email, UserRole role, AccountStatus status) {
    public AccountProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
    }
}
