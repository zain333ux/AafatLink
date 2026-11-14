package pk.edu.nu.aafatlink.application.auth;

import java.util.Objects;

import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.app.SessionUser;
import pk.edu.nu.aafatlink.domain.account.AccountProfile;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.auth.RegistrationResult;
import pk.edu.nu.aafatlink.repository.AccountRepository;
import pk.edu.nu.aafatlink.service.auth.AuthService;

/** Coordinates Auth and database-authoritative account loading outside JavaFX. */
public final class AuthenticationController {
    private final AuthService authService;
    private final AccountRepository accounts;
    private final SessionManager sessions;

    public AuthenticationController(AuthService authService, AccountRepository accounts, SessionManager sessions) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public SessionUser signIn(String email, String password) {
        AuthSession authentication = authService.signIn(email, password);
        AccountProfile profile = accounts.findCurrentAccount(authentication);
        if (profile.status() != AccountStatus.ACTIVE) {
            try {
                authService.logout(authentication.accessToken());
            } finally {
                sessions.logout();
            }
            throw new AccountAccessException("This AafatLink account is "
                    + profile.status().name().toLowerCase() + ". Contact an administrator for help.");
        }
        sessions.startAuthenticatedSession(authentication, profile);
        return sessions.currentUser().orElseThrow();
    }

    public RegistrationResult register(String displayName, String email, String password) {
        return authService.register(displayName, email, password);
    }

    public void logout() {
        String accessToken = sessions.authSession().map(AuthSession::accessToken).orElse(null);
        try {
            if (accessToken != null) {
                authService.logout(accessToken);
            }
        } finally {
            sessions.logout();
        }
    }
}
