package pk.edu.nu.aafatlink.infrastructure.supabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.auth.RegistrationResult;
import pk.edu.nu.aafatlink.service.auth.PasswordRecoveryRequest;

class SupabaseAuthClientTest {
    private static final UUID USER_ID = UUID.fromString("92ec91ce-e0a7-470c-8179-bdf49df89453");
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private HttpServer server;
    private SupabaseAuthClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/v1/", this::handle);
        server.start();
        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new SupabaseAuthClient(baseUri, "sb_publishable_test", Clock.fixed(
                Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void signsInWithPasswordAndMapsRotatableSessionTokens() {
        AuthSession session = client.signIn("requester@example.com", "StrongPass123!");

        assertEquals(USER_ID, session.userId());
        assertEquals("requester@example.com", session.email());
        assertEquals("access-token", session.accessToken());
        assertEquals("refresh-token", session.refreshToken());
        assertEquals(Instant.parse("2026-11-15T01:00:00Z"), session.expiresAt());
        assertFalse(session.toString().contains("access-token"));

        CapturedRequest request = captured.get();
        assertEquals("/auth/v1/token?grant_type=password", request.pathAndQuery());
        assertEquals("sb_publishable_test", request.apiKey());
        assertTrue(request.body().contains("requester@example.com"));
    }

    @Test
    void registersWithoutTrustingAFrontendRoleClaim() {
        RegistrationResult result = client.register("Ayesha Khan", "ayesha@example.com", "StrongPass123!");

        assertEquals(USER_ID, result.userId());
        assertTrue(result.emailConfirmationRequired());
        assertFalse(captured.get().body().contains("role"));
        assertTrue(captured.get().body().contains("display_name"));
    }

    @Test
    void rejectsWeakCredentialsBeforeNetworkCall() {
        assertThrows(IllegalArgumentException.class,
                () -> client.signIn("not-an-email", "short"));
    }

    @Test
    void refreshesRotatedTokensAndSupportsRecoveryAndLogoutEndpoints() {
        client.refresh("old-refresh-token");
        assertEquals("/auth/v1/token?grant_type=refresh_token", captured.get().pathAndQuery());
        assertTrue(captured.get().body().contains("old-refresh-token"));

        client.requestPasswordRecovery(new PasswordRecoveryRequest(
                "requester@example.com",
                URI.create("http://127.0.0.1:49152/recovery?flow_id=test-flow"),
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"));
        assertTrue(captured.get().pathAndQuery().startsWith("/auth/v1/recover?redirect_to="));
        assertTrue(captured.get().pathAndQuery().contains("127.0.0.1"));
        assertTrue(captured.get().body().contains("code_challenge"));

        client.exchangePkceCode("4c25ef5b-7bd6-42fa-ab32-229eaf4c7f1a",
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
        assertEquals("/auth/v1/token?grant_type=pkce", captured.get().pathAndQuery());

        client.updatePassword("recovery-access-token", "NewStrongPass123!");
        assertEquals("/auth/v1/user", captured.get().pathAndQuery());
        assertEquals("Bearer recovery-access-token", captured.get().authorization());

        client.logout("current-access-token");
        assertEquals("/auth/v1/logout", captured.get().pathAndQuery());
        assertEquals("Bearer current-access-token", captured.get().authorization());
    }

    @Test
    void mapsRemoteErrorsWithoutLeakingCredentials() {
        SupabaseAuthException error = assertThrows(SupabaseAuthException.class,
                () -> client.signIn("requester@example.com", "WrongPass123!"));

        assertEquals(400, error.statusCode());
        assertEquals("Invalid login credentials", error.getMessage());
        assertFalse(error.getMessage().contains("WrongPass123!"));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        captured.set(new CapturedRequest(
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("apikey"),
                exchange.getRequestHeaders().getFirst("Authorization"), body));

        if (body.contains("WrongPass123!")) {
            send(exchange, 400, "{\"message\":\"Invalid login credentials\"}");
            return;
        }

        if (exchange.getRequestURI().getPath().endsWith("/signup")) {
            send(exchange, 200, "{\"user\":{\"id\":\"" + USER_ID
                    + "\",\"email\":\"ayesha@example.com\"},\"session\":null}");
            return;
        }
        if (exchange.getRequestURI().getPath().endsWith("/recover")
                || exchange.getRequestURI().getPath().endsWith("/user")) {
            send(exchange, 200, "{}");
            return;
        }
        send(exchange, 200, "{\"access_token\":\"access-token\",\"refresh_token\":\"refresh-token\","
                + "\"expires_in\":3600,\"user\":{\"id\":\"" + USER_ID
                + "\",\"email\":\"requester@example.com\"}}");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record CapturedRequest(String pathAndQuery, String apiKey, String authorization, String body) {
    }
}
