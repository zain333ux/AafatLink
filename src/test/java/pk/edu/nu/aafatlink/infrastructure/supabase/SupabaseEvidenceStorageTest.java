package pk.edu.nu.aafatlink.infrastructure.supabase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;

class SupabaseEvidenceStorageTest {
    private static final UUID USER_ID = UUID.fromString("1260546b-6f62-4096-b705-d05be484f8d2");
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private final AtomicBoolean uncertainUpload = new AtomicBoolean();
    private HttpServer server;
    private SupabaseEvidenceStorage storage;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/storage/v1/object/request-evidence", this::handle);
        server.start();
        storage = new SupabaseEvidenceStorage(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "sb_publishable_test");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void uploadsAndDeletesOnlyWithinActorAndOperationPath(@TempDir Path directory) throws IOException {
        byte[] content = "evidence-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path file = directory.resolve("field photo.pdf");
        Files.write(file, content);
        UUID operationId = UUID.randomUUID();
        AuthSession session = new AuthSession(USER_ID, "requester@example.com", "access-token", "refresh",
                Instant.now().plusSeconds(3600));

        String objectPath = storage.upload(session, operationId,
                new EvidenceAttachment(file, "application/pdf", content.length));

        assertTrue(objectPath.startsWith(USER_ID + "/" + operationId + "/"));
        assertTrue(objectPath.endsWith("-field_photo.pdf"));
        assertEquals("POST", method.get());
        assertEquals("Bearer access-token", authorization.get());
        assertArrayEquals(content, body.get());

        storage.delete(session, objectPath);
        assertEquals("DELETE", method.get());
        assertTrue(path.get().contains("/storage/v1/object/request-evidence/" + USER_ID + "/" + operationId + "/"));
    }

    @Test
    void recoversUploadWhenObjectExistsAfterUncertainResponse(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("evidence.pdf");
        Files.writeString(file, "evidence");
        uncertainUpload.set(true);
        AuthSession session = new AuthSession(USER_ID, "requester@example.com", "access-token", "refresh",
                Instant.now().plusSeconds(3600));

        String objectPath = storage.upload(session, UUID.randomUUID(),
                new EvidenceAttachment(file, "application/pdf", Files.size(file)));

        assertTrue(objectPath.startsWith(USER_ID + "/"));
        assertEquals("HEAD", method.get());
    }

    private void handle(HttpExchange exchange) throws IOException {
        method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().getPath());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        body.set(exchange.getRequestBody().readAllBytes());
        if (uncertainUpload.get() && "POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        byte[] response = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
