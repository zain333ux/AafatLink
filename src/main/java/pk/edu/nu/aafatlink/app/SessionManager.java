package pk.edu.nu.aafatlink.app;

import java.util.Objects;
import java.util.Optional;

import pk.edu.nu.aafatlink.domain.account.AccountProfile;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;

/** Owns the active desktop session. Real token handling is introduced in Phase 3. */
public final class SessionManager {
    private SessionUser currentUser;
    private AuthSession authSession;

    public void startDemonstrationSession(UserRole role) {
        Objects.requireNonNull(role, "role");
        authSession = null;
        currentUser = new SessionUser("Demo " + role.displayName(),
                role.name().toLowerCase() + ".demo@aafatlink.local", role, true);
    }

    public void startAuthenticatedSession(AuthSession authentication, AccountProfile profile) {
        authSession = Objects.requireNonNull(authentication, "authentication");
        Objects.requireNonNull(profile, "profile");
        if (!authentication.userId().equals(profile.id())) {
            authSession = null;
            throw new IllegalArgumentException("Authentication and account identities do not match.");
        }
        currentUser = new SessionUser(profile.displayName(), profile.email(), profile.role(), false);
    }

    public Optional<SessionUser> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public Optional<AuthSession> authSession() {
        return Optional.ofNullable(authSession);
    }

    public void logout() {
        currentUser = null;
        authSession = null;
    }
}
