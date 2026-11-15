package pk.edu.nu.aafatlink.infrastructure.supabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;

class SupabaseWorkflowRepositoryTest {
    private static final UUID USER_ID = UUID.fromString("1260546b-6f62-4096-b705-d05be484f8d2");
    private static final UUID REQUEST_ID = UUID.fromString("14f0c9d0-c91b-4e8e-8fd5-f81bf0df4475");
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer server;
    private SupabaseWorkflowRepository repository;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/rpc/submit_assistance_request", this::handleSubmit);
        server.start();
        repository = new SupabaseWorkflowRepository(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "sb_publishable_test");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void submitsValidatedFieldsThroughAuthenticatedRpc() {
        AuthSession session = new AuthSession(USER_ID, "requester@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));
        AssistanceRequestDraft draft = new AssistanceRequestDraft(DisasterType.FLOOD, null,
                "Sector G-10, Islamabad", "Water entered the ground floor.",
                "Evacuation and drinking water", "Four adults and two children");

        RequestSubmissionResult result = repository.submitRequest(session, UUID.randomUUID(), USER_ID, draft);

        assertEquals(REQUEST_ID, result.requestId());
        assertEquals(RequestStatus.PENDING_VERIFICATION, result.status());
        assertEquals("Bearer user-access", authorization.get());
        assertTrue(body.get().contains("\"p_required_assistance\":\"Evacuation and drinking water\""));
        assertTrue(body.get().contains("\"p_requester_account_id\":\"" + USER_ID + "\""));
    }

    private void handleSubmit(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String response = "{\"request_id\":\"" + REQUEST_ID
                + "\",\"status\":\"PENDING_VERIFICATION\",\"possible_duplicate\":false,\"evidence_count\":0}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
