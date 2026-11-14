package pk.edu.nu.aafatlink.app;

import java.util.Objects;

import pk.edu.nu.aafatlink.domain.account.UserRole;

/** Minimal authenticated identity exposed to the presentation layer. */
public record SessionUser(String displayName, String email, UserRole role, boolean demonstrationAccount) {
    public SessionUser {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
    }
}
