package pk.edu.nu.aafatlink.application.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.auth.RegistrationResult;
import pk.edu.nu.aafatlink.service.auth.AuthService;
import pk.edu.nu.aafatlink.service.auth.PasswordRecoveryRequest;

class PasswordRecoveryCoordinatorTest {

    @Test
    void completesLoopbackPkceExchangeAndPasswordUpdate() throws Exception {
        FakeAuthService auth = new FakeAuthService();
        PasswordRecoveryCoordinator coordinator = new PasswordRecoveryCoordinator(auth);

        try (PasswordRecoveryAttempt attempt = coordinator.begin("user@example.com")) {
            assertEquals(49152, attempt.redirectUri().getPort());
            URI callback = URI.create(attempt.redirectUri() + "&code=4c25ef5b-7bd6-42fa-ab32-229eaf4c7f1a");
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(callback).GET().build(),
                    HttpResponse.BodyHandlers.discarding());

            coordinator.complete(attempt, "NewStrongPass123!", Duration.ofSeconds(2));
        }

        assertEquals("4c25ef5b-7bd6-42fa-ab32-229eaf4c7f1a", auth.exchangedCode);
        assertFalse(auth.exchangedVerifier.isBlank());
        assertEquals("NewStrongPass123!", auth.newPassword);
    }

    private static final class FakeAuthService implements AuthService {
        private String exchangedCode;
        private String exchangedVerifier;
        private String newPassword;

        @Override
        public RegistrationResult register(String displayName, String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthSession signIn(String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthSession refresh(String refreshToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void requestPasswordRecovery(PasswordRecoveryRequest request) {
        }

        @Override
        public AuthSession exchangePkceCode(String authCode, String codeVerifier) {
            exchangedCode = authCode;
            exchangedVerifier = codeVerifier;
            return new AuthSession(UUID.randomUUID(), "user@example.com", "access", "refresh",
                    Instant.now().plusSeconds(3600));
        }

        @Override
        public void updatePassword(String accessToken, String newPassword) {
            this.newPassword = newPassword;
        }

        @Override
        public void logout(String accessToken) {
        }
    }
}
