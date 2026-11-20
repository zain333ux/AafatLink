package pk.edu.nu.aafatlink.infrastructure.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerCandidate;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.volunteer.AvailabilityStatus;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerProfile;
import pk.edu.nu.aafatlink.repository.RepositoryException;
import pk.edu.nu.aafatlink.repository.VolunteerRepository;

/** Supabase Data API implementation of volunteer readiness persistence. */
public final class SupabaseVolunteerRepository implements VolunteerRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI projectUri;
    private final String publishableKey;
    private final HttpClient httpClient;

    public SupabaseVolunteerRepository(URI projectUri, String publishableKey) {
        this(projectUri, publishableKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    SupabaseVolunteerRepository(URI projectUri, String publishableKey, HttpClient httpClient) {
        this.projectUri = Objects.requireNonNull(projectUri, "projectUri");
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalArgumentException("publishableKey must not be blank");
        }
        this.publishableKey = publishableKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public List<Skill> findActiveSkills(AuthSession session) {
        JsonNode rows = send(session, "/rest/v1/skills?active=eq.true&select=id,name&order=name", "GET", null);
        requireArray(rows, "skill catalog");
        List<Skill> skills = new ArrayList<>();
        rows.forEach(row -> skills.add(new Skill(row.path("id").asLong(), required(row, "name"))));
        return List.copyOf(skills);
    }

    @Override
    public List<ServiceArea> findActiveServiceAreas(AuthSession session) {
        JsonNode rows = send(session,
                "/rest/v1/service_areas?active=eq.true&select=id,name&order=name", "GET", null);
        requireArray(rows, "service-area catalog");
        List<ServiceArea> areas = new ArrayList<>();
        rows.forEach(row -> areas.add(new ServiceArea(row.path("id").asLong(), required(row, "name"))));
        return List.copyOf(areas);
    }

    @Override
    public Optional<VolunteerProfile> findProfile(AuthSession session, UUID volunteerId) {
        Objects.requireNonNull(volunteerId, "volunteerId");
        JsonNode rows = send(session, "/rest/v1/volunteer_profiles?account_id=eq." + volunteerId
                + "&select=account_id,approval_status,availability_status,availability_notes,"
                + "available_from,available_until,version&limit=1", "GET", null);
        requireArray(rows, "volunteer profile");
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        JsonNode row = rows.get(0);
        String phone = findPhone(session, volunteerId);
        Set<Long> skillIds = ids(session, "/rest/v1/volunteer_skills?volunteer_account_id=eq."
                + volunteerId + "&select=skill_id", "skill_id");
        Set<Long> areaIds = ids(session, "/rest/v1/volunteer_service_areas?volunteer_account_id=eq."
                + volunteerId + "&select=service_area_id", "service_area_id");
        List<Skill> skills = findActiveSkills(session).stream().filter(skill -> skillIds.contains(skill.id())).toList();
        List<ServiceArea> areas = findActiveServiceAreas(session).stream()
                .filter(area -> areaIds.contains(area.id())).toList();
        try {
            return Optional.of(new VolunteerProfile(volunteerId,
                    VolunteerApprovalStatus.valueOf(required(row, "approval_status")),
                    AvailabilityStatus.valueOf(required(row, "availability_status")), phone,
                    nullable(row, "availability_notes"), instant(row, "available_from"),
                    instant(row, "available_until"), skills, areas, row.path("version").asLong()));
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid volunteer profile.", exception);
        }
    }

    @Override
    public VolunteerProfile saveReadinessAndContact(AuthSession session, VolunteerProfile profile,
            long expectedVersion) {
        Objects.requireNonNull(profile, "profile");
        ObjectNode body = JSON.createObjectNode();
        body.put("p_expected_version", expectedVersion);
        body.put("p_contact_number", profile.contactNumber());
        body.put("p_availability_status", profile.availability().name());
        putNullable(body, "p_availability_notes", profile.availabilityNotes());
        putInstant(body, "p_available_from", profile.availableFrom());
        putInstant(body, "p_available_until", profile.availableUntil());
        ArrayNode skillIds = body.putArray("p_skill_ids");
        profile.skills().forEach(skill -> skillIds.add(skill.id()));
        ArrayNode areaIds = body.putArray("p_service_area_ids");
        profile.serviceAreas().forEach(area -> areaIds.add(area.id()));
        JsonNode result = send(session, "/rest/v1/rpc/update_volunteer_readiness", "POST", body.toString());
        return new VolunteerProfile(profile.accountId(),
                VolunteerApprovalStatus.valueOf(required(result, "approval_status")),
                AvailabilityStatus.valueOf(required(result, "availability_status")),
                required(result, "contact_number"), nullable(result, "availability_notes"),
                instant(result, "available_from"), instant(result, "available_until"),
                profile.skills(), profile.serviceAreas(), result.path("version").asLong());
    }

    @Override
    public List<VolunteerCandidate> findEligible(AuthSession session, AssistanceRequest request,
            AssignmentFilter filter) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(filter, "filter");
        ObjectNode body = JSON.createObjectNode();
        body.put("p_request_id", request.id().toString());
        body.put("p_skill_id", filter.skillId());
        body.put("p_service_area_id", filter.serviceAreaId());
        putInstant(body, "p_scheduled_start_at", filter.scheduledStartAt());
        putInstant(body, "p_scheduled_end_at", filter.scheduledEndAt());
        JsonNode rows = send(session, "/rest/v1/rpc/find_eligible_volunteers", "POST", body.toString());
        requireArray(rows, "eligible-volunteer");
        List<VolunteerCandidate> candidates = new ArrayList<>();
        rows.forEach(row -> candidates.add(candidate(row)));
        return List.copyOf(candidates);
    }

    private static VolunteerCandidate candidate(JsonNode row) {
        try {
            List<Skill> skills = new ArrayList<>();
            JsonNode skillIds = row.path("skill_ids");
            JsonNode skillNames = row.path("skill_names");
            if (!skillIds.isArray() || !skillNames.isArray() || skillIds.size() != skillNames.size()) {
                throw new RepositoryException("AafatLink received invalid candidate skill data.");
            }
            for (int index = 0; index < skillIds.size(); index++) {
                skills.add(new Skill(skillIds.get(index).asLong(), skillNames.get(index).asText()));
            }
            List<ServiceArea> areas = new ArrayList<>();
            JsonNode areaIds = row.path("service_area_ids");
            JsonNode areaNames = row.path("service_area_names");
            if (!areaIds.isArray() || !areaNames.isArray() || areaIds.size() != areaNames.size()) {
                throw new RepositoryException("AafatLink received invalid candidate service-area data.");
            }
            for (int index = 0; index < areaIds.size(); index++) {
                areas.add(new ServiceArea(areaIds.get(index).asLong(), areaNames.get(index).asText()));
            }
            VolunteerProfile profile = new VolunteerProfile(
                    UUID.fromString(required(row, "volunteer_account_id")),
                    VolunteerApprovalStatus.valueOf(required(row, "approval_status")),
                    AvailabilityStatus.valueOf(required(row, "availability_status")),
                    Objects.requireNonNullElse(nullable(row, "contact_number"), ""),
                    nullable(row, "availability_notes"), instant(row, "available_from"),
                    instant(row, "available_until"), skills, areas, row.path("profile_version").asLong());
            return new VolunteerCandidate(required(row, "display_name"), profile,
                    row.path("active_assignment_count").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received invalid eligible-volunteer data.", exception);
        }
    }

    private String findPhone(AuthSession session, UUID volunteerId) {
        JsonNode rows = send(session, "/rest/v1/accounts?id=eq." + volunteerId + "&select=phone&limit=1",
                "GET", null);
        requireArray(rows, "volunteer account");
        return rows.isEmpty() ? "" : Objects.requireNonNullElse(nullable(rows.get(0), "phone"), "");
    }

    private Set<Long> ids(AuthSession session, String path, String field) {
        JsonNode rows = send(session, path, "GET", null);
        requireArray(rows, "volunteer readiness selection");
        Set<Long> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.path(field).asLong()));
        return Set.copyOf(ids);
    }

    private JsonNode send(AuthSession session, String path, String method, String body) {
        Objects.requireNonNull(session, "session");
        HttpRequest.Builder builder = HttpRequest.newBuilder(projectUri.resolve(path))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer " + session.accessToken());
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RepositoryException("AafatLink could not complete the volunteer workflow.");
            }
            return JSON.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Volunteer workflow was interrupted.", exception);
        } catch (IOException exception) {
            throw new RepositoryException("AafatLink could not read the volunteer workflow response.", exception);
        }
    }

    private static void requireArray(JsonNode node, String label) {
        if (!node.isArray()) {
            throw new RepositoryException("AafatLink received invalid " + label + " data.");
        }
    }

    private static String required(JsonNode node, String field) {
        String value = nullable(node, field);
        if (value == null) {
            throw new RepositoryException("AafatLink response omitted " + field + ".");
        }
        return value;
    }

    private static String nullable(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = nullable(node, field);
        return value == null ? null : Instant.parse(value);
    }

    private static void putNullable(ObjectNode body, String field, String value) {
        if (value == null) body.putNull(field); else body.put(field, value);
    }

    private static void putInstant(ObjectNode body, String field, Instant value) {
        if (value == null) body.putNull(field); else body.put(field, value.toString());
    }
}
