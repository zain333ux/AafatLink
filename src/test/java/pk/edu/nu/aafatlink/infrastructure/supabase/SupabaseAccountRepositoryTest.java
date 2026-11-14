package pk.edu.nu.aafatlink.infrastructure.supabase;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

import pk.edu.nu.aafatlink.domain.account.AccountProfile;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;

class SupabaseAccountRepositoryTest {
    private static final UUID USER_ID = UUID.fromString("1260546b-6f62-4096-b705-d05be484f8d2");
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;
    private SupabaseAccountRepository repository;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/accounts", this::handle);
        server.start();
        repository = new SupabaseAccountRepository(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "sb_publishable_test");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void loadsRoleAndStatusUsingTheAuthenticatedUsersToken() {
        AuthSession session = new AuthSession(USER_ID, "coordinator@example.com", "user-access", "refresh",
                Instant.parse("2026-11-15T02:00:00Z"));

        AccountProfile profile = repository.findCurrentAccount(session);

        assertEquals(USER_ID, profile.id());
        assertEquals(UserRole.COORDINATOR, profile.role());
        assertEquals(AccountStatus.ACTIVE, profile.status());
        assertEquals("Bearer user-access", authorization.get());
    }

    private void handle(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String response = "[{\"id\":\"" + USER_ID + "\",\"email\":\"coordinator@example.com\","
                + "\"display_name\":\"Sara Ahmed\",\"role\":\"COORDINATOR\",\"status\":\"ACTIVE\"}]";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
