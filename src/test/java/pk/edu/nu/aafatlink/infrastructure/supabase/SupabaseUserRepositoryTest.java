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

import pk.edu.nu.aafatlink.domain.account.AccessChanges;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;

class SupabaseUserRepositoryTest {
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID VOLUNTEER = UUID.randomUUID();
    private final AtomicReference<String> updateBody = new AtomicReference<>();
    private HttpServer server;
    private SupabaseUserRepository repository;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/accounts", exchange -> respond(exchange,
                "[{\"id\":\"" + VOLUNTEER + "\",\"display_name\":\"Volunteer One\",\"email\":\"v@example.com\",\"phone\":\"+92 311 7654321\",\"role\":\"VOLUNTEER\",\"status\":\"ACTIVE\",\"version\":2}]") );
        server.createContext("/rest/v1/volunteer_profiles", exchange -> respond(exchange,
                "[{\"account_id\":\"" + VOLUNTEER + "\",\"approval_status\":\"PENDING\"}]") );
        server.createContext("/rest/v1/role_permissions", exchange -> respond(exchange,
                "[{\"role\":\"VOLUNTEER\",\"permission_code\":\"task.update\"}]") );
        server.createContext("/rest/v1/rpc/update_user_access", exchange -> {
            updateBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"account_id\":\"" + VOLUNTEER + "\",\"display_name\":\"Volunteer One\",\"email\":\"v@example.com\",\"role\":\"VOLUNTEER\",\"status\":\"ACTIVE\",\"volunteer_approval\":\"APPROVED\",\"version\":3}");
        });
        server.start();
        repository = new SupabaseUserRepository(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "key");
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void loadsAccessConfigurationWithoutCredentialsAndInvokesAtomicUpdate() {
        AuthSession session = new AuthSession(ADMIN, "admin@example.com", "access", "refresh", Instant.now().plusSeconds(300));
        var account = repository.findUsers(session).get(0);
        assertEquals("+92 311 7654321", repository.findContactNumber(session, VOLUNTEER));
        var updated = repository.saveAccessWithAudit(session, UUID.randomUUID(), account,
                new AccessChanges(UserRole.VOLUNTEER, AccountStatus.ACTIVE,
                        VolunteerApprovalStatus.APPROVED, "Documents checked"), account.version());

        assertEquals(VolunteerApprovalStatus.APPROVED, updated.volunteerApprovalStatus().orElseThrow());
        assertTrue(updated.permissions().contains("task.update"));
        assertTrue(updateBody.get().contains("\"p_expected_version\":2"));
        assertTrue(updateBody.get().contains("\"p_volunteer_approval\":\"APPROVED\""));
    }

    private static void respond(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
}
