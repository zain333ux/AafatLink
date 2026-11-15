package pk.edu.nu.aafatlink.service.auth;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** One-use HTTP callback bound only to IPv4 loopback for JavaFX PKCE recovery. */
public final class LoopbackCallbackServer implements AutoCloseable {
    private static final String CALLBACK_PATH = "/auth/recovery";
    private final HttpServer server;
    private final String flowId;
    private final CompletableFuture<String> authCode = new CompletableFuture<>();

    private LoopbackCallbackServer(HttpServer server, String flowId) {
        this.server = server;
        this.flowId = flowId;
    }

    public static LoopbackCallbackServer start(int port) {
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Recovery callback port must be between 1024 and 65535.");
        }
        try {
            String flowId = UUID.randomUUID().toString();
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
            LoopbackCallbackServer callback = new LoopbackCallbackServer(server, flowId);
            server.createContext(CALLBACK_PATH, callback::handle);
            server.start();
            return callback;
        } catch (IOException exception) {
            throw new IllegalStateException("AafatLink could not start its secure recovery callback.", exception);
        }
    }

    public URI redirectUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + CALLBACK_PATH + "?flow_id=" + flowId);
    }

    public CompletableFuture<String> authCode() {
        return authCode;
    }

    public void failAfter(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Recovery timeout must be positive.");
        }
        CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS).execute(() ->
                authCode.completeExceptionally(new IllegalStateException("Password recovery timed out.")));
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "This recovery callback accepts GET only.");
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String returnedFlow = query.get("flow_id");
        String code = query.get("code");
        if (!flowId.equals(returnedFlow)) {
            respond(exchange, 400, "AafatLink could not validate this recovery callback.");
            return;
        }
        if (query.containsKey("error")) {
            String message = "This recovery link is invalid or expired. Request a new email and open it once on this computer.";
            authCode.completeExceptionally(new IllegalStateException(message));
            respond(exchange, 400, message);
            return;
        }
        if (code == null || code.isBlank()) {
            respond(exchange, 400, "AafatLink did not receive a recovery code. Request a new email and open it on this computer.");
            return;
        }
        authCode.complete(code);
        respond(exchange, 200, "Recovery verified. Return to AafatLink while it applies your new password.");
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> decode(parts[1]),
                        (first, ignored) -> first));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>AafatLink recovery</title>"
                + "</head><body><h1>AafatLink</h1><p>" + escapeHtml(message) + "</p></body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void close() {
        server.stop(0);
        if (!authCode.isDone()) {
            authCode.completeExceptionally(new IllegalStateException("Password recovery was cancelled."));
        }
    }
}
