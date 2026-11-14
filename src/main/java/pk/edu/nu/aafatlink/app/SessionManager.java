package pk.edu.nu.aafatlink.app;

import java.util.Objects;
import java.util.Optional;

import pk.edu.nu.aafatlink.domain.account.UserRole;

/** Owns the active desktop session. Real token handling is introduced in Phase 3. */
public final class SessionManager {
    private SessionUser currentUser;

    public void startDemonstrationSession(UserRole role) {
        Objects.requireNonNull(role, "role");
        currentUser = new SessionUser("Demo " + role.displayName(),
                role.name().toLowerCase() + ".demo@aafatlink.local", role, true);
    }

    public Optional<SessionUser> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public void logout() {
        currentUser = null;
    }
}
