package pk.edu.nu.aafatlink.infrastructure.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pk.edu.nu.aafatlink.domain.account.AccessChanges;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.UserAccount;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;
import pk.edu.nu.aafatlink.repository.RepositoryException;
import pk.edu.nu.aafatlink.repository.UserRepository;

/** RLS and RPC-backed UC12 user repository. */
public final class SupabaseUserRepository implements UserRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI projectUri;
    private final String publishableKey;
    private final HttpClient httpClient;

    public SupabaseUserRepository(URI projectUri, String publishableKey) {
        this(projectUri, publishableKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    SupabaseUserRepository(URI projectUri, String publishableKey, HttpClient httpClient) {
        this.projectUri = Objects.requireNonNull(projectUri, "projectUri");
        if (publishableKey == null || publishableKey.isBlank()) throw new IllegalArgumentException("publishableKey must not be blank");
        this.publishableKey = publishableKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public String findContactNumber(AuthSession session, UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        JsonNode rows = send(session, "/rest/v1/accounts?id=eq." + accountId + "&select=phone&limit=1", "GET", null);
        requireArray(rows);
        if (rows.isEmpty()) {
            throw new RepositoryException("The volunteer account no longer exists.");
        }
        JsonNode phone = rows.get(0).get("phone");
        return phone == null || phone.isNull() ? "" : phone.asText();
    }

    @Override
    public List<UserAccount> findUsers(AuthSession session) {
        JsonNode accounts = send(session, "/rest/v1/accounts?select=id,display_name,email,role,status,version&order=display_name", "GET", null);
        JsonNode profiles = send(session, "/rest/v1/volunteer_profiles?select=account_id,approval_status", "GET", null);
        JsonNode grants = send(session, "/rest/v1/role_permissions?select=role,permission_code", "GET", null);
        requireArray(accounts); requireArray(profiles); requireArray(grants);
        Map<UUID, VolunteerApprovalStatus> approvals = new HashMap<>();
        profiles.forEach(row -> approvals.put(UUID.fromString(required(row, "account_id")),
                VolunteerApprovalStatus.valueOf(required(row, "approval_status"))));
        Map<UserRole, Set<String>> permissions = new HashMap<>();
        grants.forEach(row -> permissions.computeIfAbsent(UserRole.valueOf(required(row, "role")), key -> new HashSet<>())
                .add(required(row, "permission_code")));
        List<UserAccount> users = new ArrayList<>();
        accounts.forEach(row -> users.add(map(row, approvals, permissions)));
        return List.copyOf(users);
    }

    @Override
    public Optional<UserAccount> findUser(AuthSession session, UUID accountId) {
        return findUsers(session).stream().filter(user -> user.id().equals(accountId)).findFirst();
    }

    @Override
    public int countActiveAdministrators(AuthSession session) {
        return (int) findUsers(session).stream().filter(user -> user.role() == UserRole.ADMINISTRATOR && user.isActive()).count();
    }

    @Override
    public UserAccount saveAccessWithAudit(AuthSession session, UUID operationId, UserAccount account,
            AccessChanges changes, long expectedVersion) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_target_account_id", account.id().toString());
        body.put("p_expected_version", expectedVersion);
        body.put("p_role", changes.role().name());
        body.put("p_status", changes.status().name());
        if (changes.volunteerApprovalStatus() == null) body.putNull("p_volunteer_approval");
        else body.put("p_volunteer_approval", changes.volunteerApprovalStatus().name());
        if (changes.reason() == null) body.putNull("p_reason"); else body.put("p_reason", changes.reason());
        JsonNode result = send(session, "/rest/v1/rpc/update_user_access", "POST", body.toString());
        UserRole role = UserRole.valueOf(required(result, "role"));
        Set<String> permissions = rolePermissions(session, role);
        return new UserAccount(UUID.fromString(required(result, "account_id")), required(result, "display_name"),
                required(result, "email"), role, AccountStatus.valueOf(required(result, "status")),
                approval(result), result.path("version").asLong(), permissions);
    }

    private Set<String> rolePermissions(AuthSession session, UserRole role) {
        JsonNode rows = send(session, "/rest/v1/role_permissions?role=eq." + role.name() + "&select=permission_code", "GET", null);
        requireArray(rows);
        Set<String> permissions = new HashSet<>();
        rows.forEach(row -> permissions.add(required(row, "permission_code")));
        return Set.copyOf(permissions);
    }

    private static UserAccount map(JsonNode row, Map<UUID, VolunteerApprovalStatus> approvals,
            Map<UserRole, Set<String>> permissions) {
        UUID id = UUID.fromString(required(row, "id"));
        UserRole role = UserRole.valueOf(required(row, "role"));
        return new UserAccount(id, required(row, "display_name"), required(row, "email"), role,
                AccountStatus.valueOf(required(row, "status")), approvals.get(id), row.path("version").asLong(),
                permissions.getOrDefault(role, Set.of()));
    }

    private JsonNode send(AuthSession session, String path, String method, String body) {
        Objects.requireNonNull(session, "session");
        HttpRequest.Builder builder = HttpRequest.newBuilder(projectUri.resolve(path)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json").header("apikey", publishableKey)
                .header("Authorization", "Bearer " + session.accessToken());
        if ("POST".equals(method)) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        else builder.GET();
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RepositoryException("AafatLink could not complete the access-control operation.");
            }
            return JSON.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Access control was interrupted.", exception);
        } catch (IOException exception) {
            throw new RepositoryException("AafatLink could not read the access-control response.", exception);
        }
    }

    private static VolunteerApprovalStatus approval(JsonNode row) {
        JsonNode value = row.get("volunteer_approval");
        return value == null || value.isNull() ? null : VolunteerApprovalStatus.valueOf(value.asText());
    }

    private static void requireArray(JsonNode node) {
        if (!node.isArray()) throw new RepositoryException("AafatLink received invalid access-control data.");
    }

    private static String required(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new RepositoryException("Access-control response omitted " + field + ".");
        }
        return value.asText();
    }
}
