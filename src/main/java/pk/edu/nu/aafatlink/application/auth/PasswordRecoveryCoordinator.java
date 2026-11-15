package pk.edu.nu.aafatlink.application.auth;

import java.time.Duration;
import java.util.Objects;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.service.auth.AuthService;
import pk.edu.nu.aafatlink.service.auth.LoopbackCallbackServer;
import pk.edu.nu.aafatlink.service.auth.PasswordRecoveryRequest;
import pk.edu.nu.aafatlink.service.auth.PkceChallenge;

/** Coordinates Supabase PKCE with a one-use JavaFX desktop loopback callback. */
public final class PasswordRecoveryCoordinator {
    private static final Duration MAXIMUM_ATTEMPT = Duration.ofMinutes(10);
    /** Exact port allow-listed in Supabase Auth for the installed desktop client. */
    public static final int CALLBACK_PORT = 49152;
    private final AuthService authService;

    public PasswordRecoveryCoordinator(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    public PasswordRecoveryAttempt begin(String email) {
        PkceChallenge challenge = PkceChallenge.generate();
        LoopbackCallbackServer callback = LoopbackCallbackServer.start(CALLBACK_PORT);
        callback.failAfter(MAXIMUM_ATTEMPT);
        try {
            authService.requestPasswordRecovery(new PasswordRecoveryRequest(
                    email, callback.redirectUri(), challenge.codeChallenge()));
            return new PasswordRecoveryAttempt(callback, challenge);
        } catch (RuntimeException exception) {
            callback.close();
            throw exception;
        }
    }

    public void complete(PasswordRecoveryAttempt attempt, String newPassword, Duration timeout) {
        Objects.requireNonNull(attempt, "attempt");
        try {
            String authCode = attempt.awaitAuthCode(timeout);
            AuthSession recoverySession = authService.exchangePkceCode(authCode, attempt.codeVerifier());
            authService.updatePassword(recoverySession.accessToken(), newPassword);
            authService.logout(recoverySession.accessToken());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PasswordRecoveryException("Password recovery was interrupted.", exception);
        } catch (Exception exception) {
            throw new PasswordRecoveryException("Password recovery could not be completed.", exception);
        }
    }
}
