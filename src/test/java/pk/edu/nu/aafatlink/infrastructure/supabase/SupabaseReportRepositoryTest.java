package pk.edu.nu.aafatlink.infrastructure.supabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.reporting.ReportFilters;

class SupabaseReportRepositoryTest {
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer server;
    private SupabaseReportRepository repository;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/v1/rpc/report_request_status", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "[{\"status\":\"VERIFIED\",\"disaster_type\":\"flood\",\"priority\":\"HIGH\",\"request_count\":4}]");
        });
        server.start();
        repository = new SupabaseReportRepository(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "key");
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void mapsAuthorizedReportRowsAndSendsEveryFilter() {
        AuthSession session = new AuthSession(UUID.randomUUID(), "coordinator@example.com", "access", "refresh",
                Instant.now().plusSeconds(300));
        var rows = repository.fetchRequestStatus(session, new ReportFilters(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 21), "flood", RequestStatus.VERIFIED, RequestPriority.HIGH, 3L));

        assertEquals("4", rows.get(0).value("Requests"));
        assertTrue(body.get().contains("\"p_service_area_id\":3"));
        assertTrue(body.get().contains("\"p_status\":\"VERIFIED\""));
    }

    private static void respond(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
}
