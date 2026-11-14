package pk.edu.nu.aafatlink.application.auth;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import pk.edu.nu.aafatlink.service.auth.LoopbackCallbackServer;
import pk.edu.nu.aafatlink.service.auth.PkceChallenge;

/** Pending one-use recovery callback and its in-memory PKCE verifier. */
public final class PasswordRecoveryAttempt implements AutoCloseable {
    private final LoopbackCallbackServer callback;
    private final PkceChallenge challenge;

    PasswordRecoveryAttempt(LoopbackCallbackServer callback, PkceChallenge challenge) {
        this.callback = Objects.requireNonNull(callback, "callback");
        this.challenge = Objects.requireNonNull(challenge, "challenge");
    }

    public URI redirectUri() {
        return callback.redirectUri();
    }

    String awaitAuthCode(Duration timeout) throws Exception {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Recovery timeout must be positive.");
        }
        return callback.authCode().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    String codeVerifier() {
        return challenge.codeVerifier();
    }

    @Override
    public String toString() {
        return "PasswordRecoveryAttempt[redirectUri=" + redirectUri() + "]";
    }

    @Override
    public void close() {
        callback.close();
    }
}
