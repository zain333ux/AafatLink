package pk.edu.nu.aafatlink.infrastructure.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.RepositoryException;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.service.evidence.EvidenceStorage;

/** Private Supabase Storage adapter for staged request evidence. */
public final class SupabaseEvidenceStorage implements EvidenceStorage {
    private static final String BUCKET = "request-evidence";
    private final URI projectUri;
    private final String publishableKey;
    private final HttpClient httpClient;

    public SupabaseEvidenceStorage(URI projectUri, String publishableKey) {
        this(projectUri, publishableKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    SupabaseEvidenceStorage(URI projectUri, String publishableKey, HttpClient httpClient) {
        this.projectUri = Objects.requireNonNull(projectUri, "projectUri");
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalArgumentException("publishableKey must not be blank");
        }
        this.publishableKey = publishableKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public String upload(AuthSession session, UUID operationId, EvidenceAttachment evidence) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(evidence, "evidence");
        String objectPath = session.userId() + "/" + operationId + "/"
                + UUID.randomUUID() + "-" + safeFileName(evidence.fileName());
        HttpRequest request;
        try {
            request = request(session, objectPath)
                    .header("Content-Type", evidence.contentType())
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofFile(evidence.path()))
                    .build();
        } catch (IOException exception) {
            throw new RepositoryException("AafatLink could not read the selected evidence file.", exception);
        }
        try {
            send(request, "upload supporting evidence");
        } catch (RepositoryException uncertainUpload) {
            if (!exists(session, objectPath)) {
                throw uncertainUpload;
            }
        }
        return objectPath;
    }

    @Override
    public void delete(AuthSession session, String objectPath) {
        Objects.requireNonNull(session, "session");
        HttpRequest request = request(session, Objects.requireNonNull(objectPath, "objectPath"))
                .DELETE().build();
        send(request, "remove uncommitted evidence");
    }

    private HttpRequest.Builder request(AuthSession session, String objectPath) {
        String encodedPath = java.util.Arrays.stream(objectPath.split("/", -1))
                .map(SupabaseEvidenceStorage::encodeSegment)
                .reduce((left, right) -> left + "/" + right)
                .orElseThrow();
        return HttpRequest.newBuilder(projectUri.resolve("/storage/v1/object/" + BUCKET + "/" + encodedPath))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer " + session.accessToken());
    }

    private void send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RepositoryException("AafatLink could not " + action + ".");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Evidence storage was interrupted.", exception);
        } catch (IOException exception) {
            throw new RepositoryException("AafatLink could not reach secure evidence storage.", exception);
        }
    }

    private boolean exists(AuthSession session, String objectPath) {
        HttpRequest request = request(session, objectPath)
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String safeFileName(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "evidence" : cleaned;
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
