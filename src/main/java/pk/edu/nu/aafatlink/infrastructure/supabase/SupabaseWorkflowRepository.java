package pk.edu.nu.aafatlink.infrastructure.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.PriorityAssessmentResult;
import pk.edu.nu.aafatlink.domain.request.RequestReviewResult;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.VerificationDecision;
import pk.edu.nu.aafatlink.repository.RepositoryException;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;

/** Supabase RPC and RLS-backed implementation of the workflow persistence boundary. */
public final class SupabaseWorkflowRepository implements WorkflowRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TRACKING_COLUMNS = "id,disaster_type,disaster_type_other,location_address,"
            + "description,required_assistance,affected_person_details,status,priority,possible_duplicate,"
            + "submitted_at,resubmitted_at,version,information_request_reason,rejection_reason";
    private final URI projectUri;
    private final String publishableKey;
    private final HttpClient httpClient;

    public SupabaseWorkflowRepository(URI projectUri, String publishableKey) {
        this(projectUri, publishableKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    SupabaseWorkflowRepository(URI projectUri, String publishableKey, HttpClient httpClient) {
        this.projectUri = Objects.requireNonNull(projectUri, "projectUri");
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalArgumentException("publishableKey must not be blank");
        }
        this.publishableKey = publishableKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public void beginRequestOperation(AuthSession session, UUID operationId, String operationType) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", Objects.requireNonNull(operationId, "operationId").toString());
        body.put("p_operation_type", Objects.requireNonNull(operationType, "operationType"));
        send(session, "/rest/v1/rpc/begin_request_operation", "POST", body.toString());
    }

    @Override
    public void registerStagedEvidence(AuthSession session, UUID evidenceId, UUID operationId,
            String objectPath, EvidenceAttachment evidence) {
        Objects.requireNonNull(evidence, "evidence");
        ObjectNode body = JSON.createObjectNode();
        body.put("p_evidence_id", Objects.requireNonNull(evidenceId, "evidenceId").toString());
        body.put("p_operation_id", Objects.requireNonNull(operationId, "operationId").toString());
        body.put("p_object_path", Objects.requireNonNull(objectPath, "objectPath"));
        body.put("p_original_filename", evidence.fileName());
        body.put("p_content_type", evidence.contentType());
        body.put("p_size_bytes", evidence.sizeBytes());
        send(session, "/rest/v1/rpc/register_staged_request_evidence", "POST", body.toString());
    }

    @Override
    public Optional<RequestSubmissionResult> reconcileRequestOperation(AuthSession session, UUID operationId) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", Objects.requireNonNull(operationId, "operationId").toString());
        JsonNode operation = send(session, "/rest/v1/rpc/reconcile_request_operation", "POST", body.toString());
        if (!"COMMITTED".equals(operation.path("status").asText())) {
            return Optional.empty();
        }
        JsonNode result = operation.get("result");
        if (result == null || !result.isObject()) {
            throw new RepositoryException("A committed request operation did not include its result.");
        }
        return Optional.of(submissionResult(result));
    }

    @Override
    public boolean abandonRequestOperation(AuthSession session, UUID operationId) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", Objects.requireNonNull(operationId, "operationId").toString());
        return send(session, "/rest/v1/rpc/abandon_request_operation", "POST", body.toString()).asBoolean(false);
    }

    @Override
    public RequestSubmissionResult submitRequest(AuthSession session, UUID operationId,
            UUID requesterId, AssistanceRequestDraft draft) {
        Objects.requireNonNull(draft, "draft");
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_requester_account_id", requesterId.toString());
        body.put("p_disaster_type", draft.disasterType().databaseValue());
        putNullable(body, "p_disaster_type_other", draft.disasterTypeOther());
        body.put("p_location_address", draft.locationAddress());
        body.put("p_description", draft.description());
        body.put("p_required_assistance", draft.requiredAssistance());
        body.put("p_affected_person_details", draft.affectedPersonDetails());

        JsonNode result = send(session, "/rest/v1/rpc/submit_assistance_request", "POST", body.toString());
        return submissionResult(result);
    }

    @Override
    public RequestSubmissionResult resubmitRequest(AuthSession session, UUID operationId,
            UUID requestId, long expectedVersion, AssistanceRequestDraft draft) {
        Objects.requireNonNull(draft, "draft");
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_request_id", requestId.toString());
        body.put("p_expected_version", expectedVersion);
        body.put("p_disaster_type", draft.disasterType().databaseValue());
        putNullable(body, "p_disaster_type_other", draft.disasterTypeOther());
        body.put("p_location_address", draft.locationAddress());
        body.put("p_description", draft.description());
        body.put("p_required_assistance", draft.requiredAssistance());
        body.put("p_affected_person_details", draft.affectedPersonDetails());
        JsonNode result = send(session, "/rest/v1/rpc/resubmit_assistance_request", "POST", body.toString());
        return submissionResult(result);
    }

    private static RequestSubmissionResult submissionResult(JsonNode result) {
        try {
            return new RequestSubmissionResult(UUID.fromString(required(result, "request_id")),
                    RequestStatus.valueOf(required(result, "status")),
                    result.path("possible_duplicate").asBoolean(false), result.path("evidence_count").asInt(0));
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid request submission response.", exception);
        }
    }

    @Override
    public List<AssistanceRequest> findRequestsByRequester(AuthSession session, UUID requesterId) {
        String path = "/rest/v1/assistance_requests?requester_account_id=eq." + requesterId
                + "&select=" + TRACKING_COLUMNS + "&order=submitted_at.desc";
        JsonNode rows = send(session, path, "GET", null);
        if (!rows.isArray()) {
            throw new RepositoryException("AafatLink received an invalid request list response.");
        }
        List<AssistanceRequest> requests = new ArrayList<>();
        rows.forEach(row -> requests.add(mapRequest(row)));
        return List.copyOf(requests);
    }

    @Override
    public Optional<AssistanceRequest> findRequestForRequester(AuthSession session, UUID requestId, UUID requesterId) {
        String path = "/rest/v1/assistance_requests?id=eq." + requestId
                + "&requester_account_id=eq." + requesterId + "&select=" + TRACKING_COLUMNS + "&limit=1";
        JsonNode rows = send(session, path, "GET", null);
        if (!rows.isArray() || rows.size() > 1) {
            throw new RepositoryException("AafatLink received an invalid request detail response.");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(enrichTracking(session, mapRequest(rows.get(0))));
    }

    @Override
    public List<AssistanceRequest> findPendingVerificationRequests(AuthSession session) {
        return findReviewQueue(session, "status=eq.PENDING_VERIFICATION&order=submitted_at.asc");
    }

    @Override
    public List<AssistanceRequest> findPrioritizableRequests(AuthSession session) {
        return findReviewQueue(session,
                "status=in.(VERIFIED,ASSIGNED,IN_PROGRESS)&order=priority.desc.nullslast,submitted_at.asc");
    }

    private List<AssistanceRequest> findReviewQueue(AuthSession session, String filters) {
        JsonNode rows = send(session, "/rest/v1/assistance_requests?" + filters + "&select=" + TRACKING_COLUMNS,
                "GET", null);
        if (!rows.isArray()) {
            throw new RepositoryException("AafatLink received an invalid coordinator queue response.");
        }
        List<AssistanceRequest> requests = new ArrayList<>();
        rows.forEach(row -> requests.add(mapRequest(row)));
        return List.copyOf(requests);
    }

    @Override
    public Optional<AssistanceRequest> findRequestForReview(AuthSession session, UUID requestId) {
        JsonNode rows = send(session, "/rest/v1/assistance_requests?id=eq." + requestId
                + "&select=" + TRACKING_COLUMNS + "&limit=1", "GET", null);
        if (!rows.isArray() || rows.size() > 1) {
            throw new RepositoryException("AafatLink received an invalid coordinator request response.");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(mapRequest(rows.get(0)));
    }

    @Override
    public RequestReviewResult decideRequestVerification(AuthSession session, UUID operationId, UUID requestId,
            long expectedVersion, VerificationDecision decision, String reason) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_request_id", requestId.toString());
        body.put("p_expected_version", expectedVersion);
        body.put("p_decision", decision.name());
        putNullable(body, "p_reason", reason);
        JsonNode result = send(session, "/rest/v1/rpc/decide_request_verification", "POST", body.toString());
        try {
            return new RequestReviewResult(UUID.fromString(required(result, "request_id")),
                    RequestStatus.valueOf(required(result, "status")), result.path("version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid verification response.", exception);
        }
    }

    @Override
    public PriorityAssessmentResult recordPriorityAssessment(AuthSession session, UUID operationId, UUID requestId,
            long expectedVersion, RequestPriority suggested, RequestPriority selected, String justification) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_request_id", requestId.toString());
        body.put("p_expected_version", expectedVersion);
        body.put("p_suggested_priority", suggested.name());
        body.put("p_selected_priority", selected.name());
        putNullable(body, "p_override_justification", justification);
        JsonNode result = send(session, "/rest/v1/rpc/record_priority_assessment", "POST", body.toString());
        try {
            String previous = nullable(result, "previous_priority");
            return new PriorityAssessmentResult(UUID.fromString(required(result, "request_id")),
                    previous == null ? null : RequestPriority.valueOf(previous),
                    RequestPriority.valueOf(required(result, "priority")),
                    RequestStatus.valueOf(required(result, "status")), result.path("version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid priority response.", exception);
        }
    }

    private AssistanceRequest enrichTracking(AuthSession session, AssistanceRequest request) {
        String assignmentPath = "/rest/v1/volunteer_assignments?request_id=eq." + request.id()
                + "&select=id,status&order=updated_at.desc&limit=1";
        JsonNode assignments = send(session, assignmentPath, "GET", null);
        if (!assignments.isArray() || assignments.size() > 1) {
            throw new RepositoryException("AafatLink received invalid assignment tracking data.");
        }
        if (assignments.isEmpty()) {
            return request;
        }

        JsonNode assignment = assignments.get(0);
        String assignmentId = required(assignment, "id");
        String assignmentState = required(assignment, "status");
        String progressPath = "/rest/v1/task_progress_updates?assignment_id=eq." + assignmentId
                + "&select=note,percentage,created_at&order=created_at.desc&limit=1";
        JsonNode progressRows = send(session, progressPath, "GET", null);
        if (!progressRows.isArray() || progressRows.size() > 1) {
            throw new RepositoryException("AafatLink received invalid progress tracking data.");
        }
        String latestProgress = progressRows.isEmpty() ? null : progressLabel(progressRows.get(0));
        return new AssistanceRequest(request.id(), request.details(), request.status(), request.priority(),
                request.possibleDuplicate(), request.submittedAt(), request.resubmittedAt(), request.version(),
                request.coordinatorReason(), assignmentState, latestProgress);
    }

    private static String progressLabel(JsonNode progress) {
        String note = required(progress, "note");
        JsonNode percentage = progress.get("percentage");
        return percentage == null || percentage.isNull() ? note : percentage.asInt() + "% · " + note;
    }

    private JsonNode send(AuthSession session, String path, String method, String body) {
        Objects.requireNonNull(session, "session");
        HttpRequest.Builder builder = HttpRequest.newBuilder(projectUri.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
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
                throw new RepositoryException("AafatLink could not complete the request workflow.");
            }
            return JSON.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("Request workflow was interrupted.", exception);
        } catch (IOException exception) {
            throw new RepositoryException("AafatLink could not read the request workflow response.", exception);
        }
    }

    private static AssistanceRequest mapRequest(JsonNode row) {
        try {
            String other = nullable(row, "disaster_type_other");
            AssistanceRequestDraft details = new AssistanceRequestDraft(
                    DisasterType.valueOf(required(row, "disaster_type").toUpperCase()), other,
                    required(row, "location_address"), required(row, "description"),
                    required(row, "required_assistance"), required(row, "affected_person_details"));
            String reason = nullable(row, "information_request_reason");
            if (reason == null) {
                reason = nullable(row, "rejection_reason");
            }
            return new AssistanceRequest(UUID.fromString(required(row, "id")), details,
                    RequestStatus.valueOf(required(row, "status")),
                    row.path("priority").isNull() ? null : RequestPriority.valueOf(row.path("priority").asText()),
                    row.path("possible_duplicate").asBoolean(false), Instant.parse(required(row, "submitted_at")),
                    instantOrNull(row, "resubmitted_at"), row.path("version").asLong(), reason, null, null);
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received invalid request data.", exception);
        }
    }

    private static void putNullable(ObjectNode body, String name, String value) {
        if (value == null) {
            body.putNull(name);
        } else {
            body.put(name, value);
        }
    }

    private static Instant instantOrNull(JsonNode object, String name) {
        String value = nullable(object, name);
        return value == null ? null : Instant.parse(value);
    }

    private static String required(JsonNode object, String name) {
        String value = nullable(object, name);
        if (value == null) {
            throw new RepositoryException("Request response is missing " + name + ".");
        }
        return value;
    }

    private static String nullable(JsonNode object, String name) {
        JsonNode value = object.get(name);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
