package pk.edu.nu.aafatlink.application.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.domain.account.AccountProfile;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.auth.RegistrationResult;
import pk.edu.nu.aafatlink.repository.AccountRepository;
import pk.edu.nu.aafatlink.service.auth.AuthService;
import pk.edu.nu.aafatlink.service.auth.PasswordRecoveryRequest;

class AuthenticationControllerTest {
    private static final UUID USER_ID = UUID.fromString("b37f56ad-6161-45d9-827d-247af7fd5cce");
    private static final AuthSession AUTH_SESSION = new AuthSession(USER_ID, "user@example.com", "access", "refresh",
            Instant.parse("2026-11-15T02:00:00Z"));

    @Test
    void signsInAndStartsRoleAwareApplicationSession() {
        SessionManager sessions = new SessionManager();
        FakeAuthService auth = new FakeAuthService();
        AccountRepository accounts = ignored -> new AccountProfile(USER_ID, "Ayesha Khan", "user@example.com",
                UserRole.VOLUNTEER, AccountStatus.ACTIVE);
        AuthenticationController controller = new AuthenticationController(auth, accounts, sessions);

        controller.signIn("user@example.com", "StrongPass123!");

        assertEquals(UserRole.VOLUNTEER, sessions.currentUser().orElseThrow().role());
        assertTrue(sessions.authSession().isPresent());
    }

    @Test
    void refusesADeactivatedDatabaseAccountAndClearsAuthSession() {
        SessionManager sessions = new SessionManager();
        FakeAuthService auth = new FakeAuthService();
        AccountRepository accounts = ignored -> new AccountProfile(USER_ID, "Ayesha Khan", "user@example.com",
                UserRole.REQUESTER, AccountStatus.SUSPENDED);
        AuthenticationController controller = new AuthenticationController(auth, accounts, sessions);

        assertThrows(AccountAccessException.class,
                () -> controller.signIn("user@example.com", "StrongPass123!"));
        assertTrue(sessions.currentUser().isEmpty());
        assertTrue(auth.loggedOut);
    }

    @Test
    void localLogoutStillCompletesWhenRemoteRevocationFails() {
        SessionManager sessions = new SessionManager();
        FakeAuthService auth = new FakeAuthService();
        AccountRepository accounts = ignored -> new AccountProfile(USER_ID, "Ayesha Khan", "user@example.com",
                UserRole.REQUESTER, AccountStatus.ACTIVE);
        AuthenticationController controller = new AuthenticationController(auth, accounts, sessions);
        controller.signIn("user@example.com", "StrongPass123!");
        auth.failLogout = true;

        assertThrows(RuntimeException.class, controller::logout);
        assertTrue(sessions.currentUser().isEmpty());
        assertTrue(sessions.authSession().isEmpty());
    }

    private static final class FakeAuthService implements AuthService {
        private boolean loggedOut;
        private boolean failLogout;

        @Override
        public RegistrationResult register(String displayName, String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthSession signIn(String email, String password) {
            return AUTH_SESSION;
        }

        @Override
        public AuthSession refresh(String refreshToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void requestPasswordRecovery(PasswordRecoveryRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthSession exchangePkceCode(String authCode, String codeVerifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updatePassword(String accessToken, String newPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout(String accessToken) {
            loggedOut = true;
            if (failLogout) {
                throw new RuntimeException("remote unavailable");
            }
        }
    }
}
