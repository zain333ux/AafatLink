package pk.edu.nu.aafatlink.infrastructure.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.auth.RegistrationResult;
import pk.edu.nu.aafatlink.service.auth.AuthService;
import pk.edu.nu.aafatlink.service.auth.PasswordRecoveryRequest;

/** Supabase GoTrue adapter implemented with the Java HTTP Client. */
public final class SupabaseAuthClient implements AuthService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI projectUri;
    private final String publishableKey;
    private final Clock clock;
    private final HttpClient httpClient;

    public SupabaseAuthClient(URI projectUri, String publishableKey) {
        this(projectUri, publishableKey, Clock.systemUTC());
    }

    public SupabaseAuthClient(URI projectUri, String publishableKey, Clock clock) {
        this(projectUri, publishableKey, clock, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    SupabaseAuthClient(URI projectUri, String publishableKey, Clock clock, HttpClient httpClient) {
        this.projectUri = Objects.requireNonNull(projectUri, "projectUri");
        this.publishableKey = requireText(publishableKey, "publishableKey");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public RegistrationResult register(String displayName, String email, String password) {
        validateCredentials(email, password);
        String safeName = requireText(displayName, "displayName").trim();
        if (safeName.length() < 2 || safeName.length() > 120) {
            throw new IllegalArgumentException("Display name must contain 2 to 120 characters.");
        }
        JsonNode response = post("/auth/v1/signup", Map.of(
                "email", email.trim(),
                "password", password,
                "data", Map.of("display_name", safeName)), null);
        JsonNode user = requiredObject(response, "user");
        return new RegistrationResult(
                UUID.fromString(requiredText(user, "id")),
                requiredText(user, "email"),
                response.path("session").isMissingNode() || response.path("session").isNull());
    }

    @Override
    public AuthSession signIn(String email, String password) {
        validateCredentials(email, password);
        JsonNode response = post("/auth/v1/token?grant_type=password",
                Map.of("email", email.trim(), "password", password), null);
        return mapSession(response);
    }

    @Override
    public AuthSession refresh(String refreshToken) {
        JsonNode response = post("/auth/v1/token?grant_type=refresh_token",
                Map.of("refresh_token", requireText(refreshToken, "refreshToken")), null);
        return mapSession(response);
    }

    @Override
    public void requestPasswordRecovery(PasswordRecoveryRequest request) {
        Objects.requireNonNull(request, "request");
        validateEmail(request.email());
        String redirect = URLEncoder.encode(request.redirectUri().toString(), StandardCharsets.UTF_8);
        post("/auth/v1/recover?redirect_to=" + redirect, Map.of(
                "email", request.email().trim(),
                "code_challenge", requireText(request.codeChallenge(), "codeChallenge"),
                "code_challenge_method", "s256"), null);
    }

    @Override
    public AuthSession exchangePkceCode(String authCode, String codeVerifier) {
        JsonNode response = post("/auth/v1/token?grant_type=pkce", Map.of(
                "auth_code", requireText(authCode, "authCode"),
                "code_verifier", requireText(codeVerifier, "codeVerifier")), null);
        return mapSession(response);
    }

    @Override
    public void updatePassword(String accessToken, String newPassword) {
        validatePassword(newPassword);
        post("/auth/v1/user", Map.of("password", newPassword), requireText(accessToken, "accessToken"));
    }

    @Override
    public void logout(String accessToken) {
        post("/auth/v1/logout", Map.of(), requireText(accessToken, "accessToken"));
    }

    private AuthSession mapSession(JsonNode response) {
        JsonNode user = requiredObject(response, "user");
        long expiresIn = response.path("expires_in").asLong(0);
        if (expiresIn <= 0) {
            throw new SupabaseAuthException("Supabase Auth returned an invalid session lifetime.", null);
        }
        return new AuthSession(
                UUID.fromString(requiredText(user, "id")),
                requiredText(user, "email"),
                requiredText(response, "access_token"),
                requiredText(response, "refresh_token"),
                Instant.now(clock).plusSeconds(expiresIn));
    }

    private JsonNode post(String path, Object body, String accessToken) {
        String jsonBody;
        try {
            jsonBody = JSON.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode an authentication request.", exception);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(projectUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer " + (accessToken == null ? publishableKey : accessToken))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SupabaseAuthException(response.statusCode(), safeErrorMessage(response.body()));
            }
            if (response.body() == null || response.body().isBlank()) {
                return JSON.createObjectNode();
            }
            return JSON.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SupabaseAuthException("Authentication was interrupted. Please try again.", exception);
        } catch (IOException exception) {
            throw new SupabaseAuthException("AafatLink could not reach the authentication service.", exception);
        }
    }

    private static String safeErrorMessage(String body) {
        try {
            JsonNode error = JSON.readTree(body == null ? "{}" : body);
            String message = error.path("msg").asText(error.path("message").asText(""));
            return message.isBlank() ? "The authentication request was rejected." : message;
        } catch (JsonProcessingException exception) {
            return "The authentication request was rejected.";
        }
    }

    private static JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isObject()) {
            throw new SupabaseAuthException("Supabase Auth returned an incomplete response.", null);
        }
        return value;
    }

    private static String requiredText(JsonNode parent, String field) {
        String value = parent.path(field).asText("");
        if (value.isBlank()) {
            throw new SupabaseAuthException("Supabase Auth returned an incomplete response.", null);
        }
        return value;
    }

    private static void validateCredentials(String email, String password) {
        validateEmail(email);
        validatePassword(password);
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
