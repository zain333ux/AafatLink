package pk.edu.nu.aafatlink.infrastructure.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pk.edu.nu.aafatlink.domain.account.AccountProfile;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.AccountRepository;
import pk.edu.nu.aafatlink.repository.RepositoryException;

/** Reads the RLS-protected application account through PostgREST. */
public final class SupabaseAccountRepository implements AccountRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI projectUri;
    private final String publishableKey;
    private final HttpClient httpClient;

    public SupabaseAccountRepository(URI projectUri, String publishableKey) {
        this(projectUri, publishableKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    SupabaseAccountRepository(URI projectUri, String publishableKey, HttpClient httpClient) {
        this.projectUri = Objects.requireNonNull(projectUri, "projectUri");
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalArgumentException("publishableKey must not be blank");
        }
        this.publishableKey = publishableKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public AccountProfile findCurrentAccount(AuthSession session) {
        Objects.requireNonNull(session, "session");
        String query = "/rest/v1/accounts?id=eq." + session.userId()
                + "&select=id,email,display_name,role,status";
        HttpRequest request = HttpRequest.newBuilder(projectUri.resolve(query))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer " + session.accessToken())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RepositoryException("AafatLink could not load the authorized account profile.");
            }
            JsonNode rows = JSON.readTree(response.body());
            if (!rows.isArray() || rows.size() != 1) {
                throw new RepositoryException("No active AafatLink profile is linked to this identity.");
            }
            JsonNode row = rows.get(0);
            UUID id = UUID.fromString(required(row, "id"));
            if (!id.equals(session.userId())) {
                throw new RepositoryException("The returned account identity did not match the authenticated user.");
            }
            return new AccountProfile(id, required(row, "display_name"), required(row, "email"),
                    UserRole.valueOf(required(row, "role")), AccountStatus.valueOf(required(row, "status")));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Account loading was interrupted.", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid account response.", exception);
        }
    }

    private static String required(JsonNode object, String name) {
        String value = object.path(name).asText("");
        if (value.isBlank()) {
            throw new RepositoryException("Account response is missing " + name + ".");
        }
        return value;
    }
}
