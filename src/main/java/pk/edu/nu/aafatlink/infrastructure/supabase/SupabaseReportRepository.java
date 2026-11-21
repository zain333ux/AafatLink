package pk.edu.nu.aafatlink.infrastructure.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;
import pk.edu.nu.aafatlink.repository.RepositoryException;
import pk.edu.nu.aafatlink.reporting.ReportFilters;
import pk.edu.nu.aafatlink.reporting.ReportRow;

/** Supabase implementation of the read-only UC11 report repository. */
public final class SupabaseReportRepository implements ReportRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI projectUri;
    private final String publishableKey;
    private final HttpClient httpClient;

    public SupabaseReportRepository(URI projectUri, String publishableKey) {
        this(projectUri, publishableKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    SupabaseReportRepository(URI projectUri, String publishableKey, HttpClient httpClient) {
        this.projectUri = Objects.requireNonNull(projectUri, "projectUri");
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalArgumentException("publishableKey must not be blank");
        }
        this.publishableKey = publishableKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public List<ReportRow> fetchRequestStatus(AuthSession session, ReportFilters filters) {
        return fetch(session, "report_request_status", filters, Map.of(
                "Status", "status", "Disaster type", "disaster_type", "Priority", "priority", "Requests", "request_count"));
    }

    @Override
    public List<ReportRow> fetchOutstandingRequests(AuthSession session, ReportFilters filters) {
        return fetch(session, "report_outstanding_requests", filters, Map.of(
                "Request ID", "request_id", "Submitted", "submitted_at", "Disaster type", "disaster_type",
                "Status", "status", "Priority", "priority", "Location", "location", "Waiting hours", "waiting_hours"));
    }

    @Override
    public List<ReportRow> fetchVolunteerWorkload(AuthSession session, ReportFilters filters) {
        return fetch(session, "report_volunteer_workload", filters, Map.of(
                "Volunteer", "volunteer_name", "Assignments", "assignment_count", "Active", "active_count",
                "Completed", "completed_count"));
    }

    @Override
    public List<ReportRow> fetchResponsePerformance(AuthSession session, ReportFilters filters) {
        return fetch(session, "report_response_performance", filters, Map.of(
                "Requests", "request_count", "Closed", "closed_count", "Closure rate", "closure_rate",
                "Avg verification hours", "avg_verification_hours", "Avg assignment hours", "avg_assignment_hours",
                "Avg resolution hours", "avg_resolution_hours"));
    }

    private List<ReportRow> fetch(AuthSession session, String function, ReportFilters filters,
            Map<String, String> mapping) {
        Objects.requireNonNull(filters, "filters");
        JsonNode rows = send(session, function, filterBody(filters));
        if (!rows.isArray()) throw new RepositoryException("AafatLink received invalid report data.");
        List<ReportRow> result = new ArrayList<>();
        rows.forEach(row -> {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            mapping.forEach((label, field) -> values.put(label, text(row, field)));
            result.add(new ReportRow(values));
        });
        return List.copyOf(result);
    }

    private static ObjectNode filterBody(ReportFilters filters) {
        ObjectNode body = JSON.createObjectNode();
        put(body, "p_start_date", filters.startDate() == null ? null : filters.startDate().toString());
        put(body, "p_end_date", filters.endDate() == null ? null : filters.endDate().toString());
        put(body, "p_disaster_type", filters.disasterType());
        put(body, "p_status", filters.status() == null ? null : filters.status().name());
        put(body, "p_priority", filters.priority() == null ? null : filters.priority().name());
        if (filters.serviceAreaId() == null) body.putNull("p_service_area_id");
        else body.put("p_service_area_id", filters.serviceAreaId());
        return body;
    }

    private JsonNode send(AuthSession session, String function, ObjectNode body) {
        Objects.requireNonNull(session, "session");
        HttpRequest request = HttpRequest.newBuilder(projectUri.resolve("/rest/v1/rpc/" + function))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                .header("Content-Type", "application/json").header("apikey", publishableKey)
                .header("Authorization", "Bearer " + session.accessToken())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RepositoryException("The report could not be generated. No partial result was shown.");
            }
            return JSON.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Report generation was interrupted.", exception);
        } catch (IOException exception) {
            throw new RepositoryException("AafatLink could not read the report response.", exception);
        }
    }

    private static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? "—" : value.asText();
    }

    private static void put(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
