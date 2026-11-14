package pk.edu.nu.aafatlink.domain.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Authenticated Supabase session. Its string form deliberately omits credentials. */
public final class AuthSession {
    private final UUID userId;
    private final String email;
    private final String accessToken;
    private final String refreshToken;
    private final Instant expiresAt;

    public AuthSession(UUID userId, String email, String accessToken, String refreshToken, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.email = Objects.requireNonNull(email, "email");
        this.accessToken = requireText(accessToken, "accessToken");
        this.refreshToken = requireText(refreshToken, "refreshToken");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public UUID userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "AuthSession[userId=" + userId + ", email=" + email + ", expiresAt=" + expiresAt + "]";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
