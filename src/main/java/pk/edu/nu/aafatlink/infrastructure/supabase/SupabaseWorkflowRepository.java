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
import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentCreationResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentStatus;
import pk.edu.nu.aafatlink.domain.assignment.ClosureDecision;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReview;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReviewResult;
import pk.edu.nu.aafatlink.domain.assignment.CompletionEvidence;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdate;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdateResult;
import pk.edu.nu.aafatlink.domain.assignment.ReassignmentResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletion;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletionResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerAssignment;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.PriorityAssessmentResult;
import pk.edu.nu.aafatlink.domain.request.RequestReviewResult;
import pk.edu.nu.aafatlink.domain.request.RequestReviewDetails;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.RequesterContact;
import pk.edu.nu.aafatlink.domain.request.ReviewEvidence;
import pk.edu.nu.aafatlink.domain.request.PossibleDuplicateMatch;
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
    public RequestSubmissionResult recordCoordinatorRequest(AuthSession session, UUID operationId,
            RequesterContact requester, AssistanceRequestDraft draft) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(draft, "draft");
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_requester_contact_name", requester.name());
        body.put("p_requester_contact_no", requester.contactNumber());
        body.put("p_disaster_type", draft.disasterType().databaseValue());
        putNullable(body, "p_disaster_type_other", draft.disasterTypeOther());
        body.put("p_location_address", draft.locationAddress());
        body.put("p_description", draft.description());
        body.put("p_required_assistance", draft.requiredAssistance());
        body.put("p_affected_person_details", draft.affectedPersonDetails());
        JsonNode result = send(session, "/rest/v1/rpc/record_coordinator_assistance_request", "POST",
                body.toString());
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
    public Optional<RequestReviewDetails> findRequestReviewDetails(AuthSession session, UUID requestId) {
        Optional<AssistanceRequest> request = findRequestForReview(session, requestId);
        if (request.isEmpty()) return Optional.empty();
        ObjectNode body = JSON.createObjectNode();
        body.put("p_request_id", requestId.toString());
        JsonNode result = send(session, "/rest/v1/rpc/get_request_review_details", "POST", body.toString());
        try {
            List<ReviewEvidence> evidence = new ArrayList<>();
            JsonNode evidenceRows = result.path("evidence");
            if (!evidenceRows.isArray()) throw new RepositoryException("Invalid request evidence response.");
            evidenceRows.forEach(row -> evidence.add(new ReviewEvidence(UUID.fromString(required(row, "id")),
                    required(row, "file_name"), required(row, "content_type"), row.path("size_bytes").asLong(),
                    Instant.parse(required(row, "uploaded_at")))));
            List<PossibleDuplicateMatch> duplicates = new ArrayList<>();
            JsonNode duplicateRows = result.path("possible_duplicates");
            if (!duplicateRows.isArray()) throw new RepositoryException("Invalid duplicate-match response.");
            duplicateRows.forEach(row -> duplicates.add(new PossibleDuplicateMatch(
                    UUID.fromString(required(row, "request_id")), RequestStatus.valueOf(required(row, "status")),
                    row.path("priority").isNull() ? null : RequestPriority.valueOf(row.path("priority").asText()),
                    Instant.parse(required(row, "submitted_at")))));
            return Optional.of(new RequestReviewDetails(request.get(), nullable(result, "requester_name"),
                    nullable(result, "requester_contact"), evidence, duplicates));
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received invalid request-review details.", exception);
        }
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

    @Override
    public List<AssignmentContext> findPendingAssignments(AuthSession session, UUID volunteerId) {
        String path = "/rest/v1/volunteer_assignments?volunteer_account_id=eq." + volunteerId
                + "&status=eq.PENDING_ACCEPTANCE&select=id,request_id,volunteer_account_id,status,assigned_at,"
                + "scheduled_start_at,scheduled_end_at,version&order=assigned_at.desc";
        JsonNode rows = send(session, path, "GET", null);
        if (!rows.isArray()) {
            throw new RepositoryException("AafatLink received invalid pending-assignment data.");
        }
        List<AssignmentContext> assignments = new ArrayList<>();
        rows.forEach(row -> {
            VolunteerAssignment assignment = mapAssignment(row);
            AssistanceRequest request = findRequestForReview(session, assignment.requestId())
                    .orElseThrow(() -> new RepositoryException("An assignment request could not be loaded."));
            assignments.add(new AssignmentContext(assignment, request));
        });
        return List.copyOf(assignments);
    }

    @Override
    public Optional<AssignmentContext> findAssignmentContext(AuthSession session, UUID assignmentId) {
        String path = "/rest/v1/volunteer_assignments?id=eq." + assignmentId
                + "&select=id,request_id,volunteer_account_id,status,assigned_at,scheduled_start_at,"
                + "scheduled_end_at,version&limit=1";
        JsonNode rows = send(session, path, "GET", null);
        if (!rows.isArray() || rows.size() > 1) {
            throw new RepositoryException("AafatLink received invalid assignment detail data.");
        }
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        VolunteerAssignment assignment = mapAssignment(rows.get(0));
        return findRequestForReview(session, assignment.requestId())
                .map(request -> new AssignmentContext(assignment, request));
    }

    @Override
    public AssignmentResponseResult respondToAssignment(AuthSession session, UUID operationId,
            UUID assignmentId, long expectedVersion, boolean accept, String declineReason) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_assignment_id", assignmentId.toString());
        body.put("p_expected_version", expectedVersion);
        body.put("p_accept", accept);
        putNullable(body, "p_decline_reason", declineReason);
        JsonNode result = send(session, "/rest/v1/rpc/respond_to_assignment", "POST", body.toString());
        try {
            return new AssignmentResponseResult(UUID.fromString(required(result, "assignment_id")),
                    AssignmentStatus.valueOf(required(result, "assignment_status")),
                    UUID.fromString(required(result, "request_id")),
                    RequestStatus.valueOf(required(result, "request_status")),
                    result.path("assignment_version").asLong(), result.path("request_version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid assignment response.", exception);
        }
    }

    @Override
    public List<AssistanceRequest> findAssignableRequests(AuthSession session) {
        return findReviewQueue(session,
                "status=eq.VERIFIED&priority=not.is.null&order=priority.asc,submitted_at.asc");
    }

    @Override
    public List<AssignmentContext> findActiveAssignmentsForCoordinator(AuthSession session) {
        String path = "/rest/v1/volunteer_assignments?status=in.(PENDING_ACCEPTANCE,ACCEPTED,IN_PROGRESS)"
                + "&select=id,request_id,volunteer_account_id,status,assigned_at,scheduled_start_at,"
                + "scheduled_end_at,version&order=assigned_at.desc";
        JsonNode rows = send(session, path, "GET", null);
        if (!rows.isArray()) {
            throw new RepositoryException("AafatLink received invalid active-assignment data.");
        }
        List<AssignmentContext> assignments = new ArrayList<>();
        rows.forEach(row -> {
            VolunteerAssignment assignment = mapAssignment(row);
            AssistanceRequest request = findRequestForReview(session, assignment.requestId())
                    .orElseThrow(() -> new RepositoryException("An active assignment request could not be loaded."));
            assignments.add(new AssignmentContext(assignment, request));
        });
        return List.copyOf(assignments);
    }

    @Override
    public AssignmentCreationResult assignVolunteer(AuthSession session, UUID operationId,
            AssistanceRequest request, UUID volunteerId, AssignmentFilter filter) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(filter, "filter");
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_request_id", request.id().toString());
        body.put("p_volunteer_account_id", volunteerId.toString());
        body.put("p_expected_request_version", request.version());
        body.put("p_skill_id", filter.skillId());
        body.put("p_service_area_id", filter.serviceAreaId());
        putInstant(body, "p_scheduled_start_at", filter.scheduledStartAt());
        putInstant(body, "p_scheduled_end_at", filter.scheduledEndAt());
        JsonNode result = send(session, "/rest/v1/rpc/assign_volunteer_matching", "POST", body.toString());
        try {
            return new AssignmentCreationResult(UUID.fromString(required(result, "assignment_id")),
                    UUID.fromString(required(result, "request_id")),
                    AssignmentStatus.valueOf(required(result, "assignment_status")),
                    RequestStatus.valueOf(required(result, "request_status")),
                    result.path("request_version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid assignment result.", exception);
        }
    }

    @Override
    public ReassignmentResult reassignVolunteer(AuthSession session, UUID operationId, UUID assignmentId,
            long expectedAssignmentVersion, String reason) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_assignment_id", assignmentId.toString());
        body.put("p_expected_assignment_version", expectedAssignmentVersion);
        body.put("p_reason", reason);
        JsonNode result = send(session, "/rest/v1/rpc/reassign_volunteer", "POST", body.toString());
        try {
            return new ReassignmentResult(UUID.fromString(required(result, "assignment_id")),
                    AssignmentStatus.valueOf(required(result, "assignment_status")),
                    UUID.fromString(required(result, "request_id")),
                    RequestStatus.valueOf(required(result, "request_status")),
                    result.path("assignment_version").asLong(), result.path("request_version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid reassignment result.", exception);
        }
    }

    @Override
    public List<TaskContext> findActiveTasks(AuthSession session, UUID volunteerId) {
        String path = "/rest/v1/volunteer_assignments?volunteer_account_id=eq." + volunteerId
                + "&status=in.(ACCEPTED,IN_PROGRESS)&select=id,request_id,volunteer_account_id,status,assigned_at,"
                + "scheduled_start_at,scheduled_end_at,version&order=updated_at.desc";
        return taskContexts(session, send(session, path, "GET", null), "active task");
    }

    @Override
    public Optional<TaskContext> findTaskContext(AuthSession session, UUID assignmentId) {
        String path = "/rest/v1/volunteer_assignments?id=eq." + assignmentId
                + "&select=id,request_id,volunteer_account_id,status,assigned_at,scheduled_start_at,"
                + "scheduled_end_at,version&limit=1";
        JsonNode rows = send(session, path, "GET", null);
        if (!rows.isArray() || rows.size() > 1) {
            throw new RepositoryException("AafatLink received invalid task details.");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(loadTaskContext(session, mapAssignment(rows.get(0))));
    }

    @Override
    public ProgressUpdateResult saveProgressAndStatus(AuthSession session, UUID operationId, ProgressUpdate update,
            UUID requestId, long expectedAssignmentVersion, long expectedRequestVersion) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_update_id", update.updateId().toString());
        body.put("p_assignment_id", update.assignmentId().toString());
        body.put("p_request_id", requestId.toString());
        body.put("p_expected_assignment_version", expectedAssignmentVersion);
        body.put("p_expected_request_version", expectedRequestVersion);
        body.put("p_note", update.progressNote());
        if (update.completionPercent() == null) body.putNull("p_percentage");
        else body.put("p_percentage", update.completionPercent());
        return progressResult(send(session, "/rest/v1/rpc/record_task_progress", "POST", body.toString()));
    }

    @Override
    public Optional<ProgressUpdateResult> reconcileProgressOperation(AuthSession session, UUID operationId) {
        return reconcileOperation(session, operationId).map(SupabaseWorkflowRepository::progressResult);
    }

    @Override
    public void beginTaskOperation(AuthSession session, UUID operationId, String operationType) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_operation_type", operationType);
        send(session, "/rest/v1/rpc/begin_task_operation", "POST", body.toString());
    }

    @Override
    public TaskCompletionResult saveCompletionAndStatus(AuthSession session, UUID operationId,
            TaskCompletion completion, UUID requestId, long expectedAssignmentVersion, long expectedRequestVersion) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_completion_id", completion.completionId().toString());
        body.put("p_assignment_id", completion.assignmentId().toString());
        body.put("p_request_id", requestId.toString());
        body.put("p_expected_assignment_version", expectedAssignmentVersion);
        body.put("p_expected_request_version", expectedRequestVersion);
        body.put("p_remarks", completion.remarks());
        return completionResult(send(session, "/rest/v1/rpc/complete_assistance_task", "POST", body.toString()));
    }

    @Override
    public Optional<TaskCompletionResult> reconcileTaskCompletion(AuthSession session, UUID operationId) {
        return reconcileOperation(session, operationId).map(SupabaseWorkflowRepository::completionResult);
    }

    @Override
    public boolean abandonTaskOperation(AuthSession session, UUID operationId) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        return send(session, "/rest/v1/rpc/abandon_task_operation", "POST", body.toString()).asBoolean(false);
    }

    @Override
    public List<TaskContext> findRequestsAwaitingClosure(AuthSession session) {
        String path = "/rest/v1/volunteer_assignments?status=eq.AWAITING_CLOSURE_REVIEW"
                + "&select=id,request_id,volunteer_account_id,status,assigned_at,scheduled_start_at,"
                + "scheduled_end_at,version&order=updated_at.asc";
        return taskContexts(session, send(session, path, "GET", null), "closure queue");
    }

    @Override
    public Optional<TaskContext> findClosureContext(AuthSession session, UUID requestId) {
        String path = "/rest/v1/volunteer_assignments?request_id=eq." + requestId
                + "&status=eq.AWAITING_CLOSURE_REVIEW&select=id,request_id,volunteer_account_id,status,assigned_at,"
                + "scheduled_start_at,scheduled_end_at,version&limit=1";
        JsonNode rows = send(session, path, "GET", null);
        if (!rows.isArray() || rows.size() > 1) {
            throw new RepositoryException("AafatLink received invalid closure-review details.");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(loadTaskContext(session, mapAssignment(rows.get(0))));
    }

    @Override
    public ClosureReviewResult saveClosureAndStatus(AuthSession session, UUID operationId, ClosureReview review,
            long expectedAssignmentVersion, long expectedRequestVersion) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        body.put("p_review_id", review.reviewId().toString());
        body.put("p_request_id", review.requestId().toString());
        body.put("p_assignment_id", review.assignmentId().toString());
        body.put("p_expected_assignment_version", expectedAssignmentVersion);
        body.put("p_expected_request_version", expectedRequestVersion);
        body.put("p_decision", review.decision().name());
        putNullable(body, "p_remarks", review.remarks().orElse(null));
        return closureResult(send(session, "/rest/v1/rpc/review_request_closure", "POST", body.toString()));
    }

    @Override
    public Optional<ClosureReviewResult> reconcileClosureOperation(AuthSession session, UUID operationId) {
        return reconcileOperation(session, operationId).map(SupabaseWorkflowRepository::closureResult);
    }

    private List<TaskContext> taskContexts(AuthSession session, JsonNode rows, String label) {
        if (!rows.isArray()) {
            throw new RepositoryException("AafatLink received invalid " + label + " data.");
        }
        List<TaskContext> contexts = new ArrayList<>();
        rows.forEach(row -> contexts.add(loadTaskContext(session, mapAssignment(row))));
        return List.copyOf(contexts);
    }

    private TaskContext loadTaskContext(AuthSession session, VolunteerAssignment assignment) {
        AssistanceRequest request = findRequestForReview(session, assignment.requestId())
                .orElseThrow(() -> new RepositoryException("The task request could not be loaded."));
        JsonNode progressRows = send(session, "/rest/v1/task_progress_updates?assignment_id=eq." + assignment.id()
                + "&select=id,assignment_id,volunteer_account_id,note,percentage,created_at&order=created_at.asc",
                "GET", null);
        if (!progressRows.isArray()) {
            throw new RepositoryException("AafatLink received invalid progress history.");
        }
        List<ProgressUpdate> progress = new ArrayList<>();
        progressRows.forEach(row -> progress.add(mapProgress(row)));

        JsonNode completionRows = send(session, "/rest/v1/task_completions?assignment_id=eq." + assignment.id()
                + "&select=id,assignment_id,volunteer_account_id,remarks,completed_at"
                + "&order=completed_at.desc&limit=1", "GET", null);
        if (!completionRows.isArray() || completionRows.size() > 1) {
            throw new RepositoryException("AafatLink received invalid completion details.");
        }
        TaskCompletion completion = completionRows.isEmpty() ? null : mapCompletion(completionRows.get(0));
        List<CompletionEvidence> evidence = completion == null ? List.of()
                : mapEvidence(send(session, "/rest/v1/request_evidence?task_completion_id=eq."
                        + completion.completionId()
                        + "&state=eq.COMMITTED&select=id,original_filename,content_type,size_bytes&order=created_at.asc",
                        "GET", null));

        JsonNode reviewRows = send(session, "/rest/v1/closure_reviews?assignment_id=eq." + assignment.id()
                + "&select=id,request_id,assignment_id,coordinator_account_id,decision,reason,created_at"
                + "&order=created_at.desc&limit=1", "GET", null);
        if (!reviewRows.isArray() || reviewRows.size() > 1) {
            throw new RepositoryException("AafatLink received invalid closure history.");
        }
        ClosureReview latestReview = reviewRows.isEmpty() ? null : mapClosureReview(reviewRows.get(0));
        return new TaskContext(new AssignmentContext(assignment, request), progress, completion, evidence, latestReview);
    }

    private Optional<JsonNode> reconcileOperation(AuthSession session, UUID operationId) {
        ObjectNode body = JSON.createObjectNode();
        body.put("p_operation_id", operationId.toString());
        JsonNode operation = send(session, "/rest/v1/rpc/reconcile_workflow_operation", "POST", body.toString());
        if (!"COMMITTED".equals(operation.path("status").asText())) {
            return Optional.empty();
        }
        JsonNode result = operation.get("result");
        if (result == null || !result.isObject()) {
            throw new RepositoryException("A committed workflow operation did not include its result.");
        }
        return Optional.of(result);
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

    private static ProgressUpdate mapProgress(JsonNode row) {
        return new ProgressUpdate(UUID.fromString(required(row, "id")),
                UUID.fromString(required(row, "assignment_id")),
                UUID.fromString(required(row, "volunteer_account_id")),
                required(row, "note"), row.path("percentage").isNull() ? null : row.path("percentage").asInt(),
                Instant.parse(required(row, "created_at")));
    }

    private static TaskCompletion mapCompletion(JsonNode row) {
        return new TaskCompletion(UUID.fromString(required(row, "id")),
                UUID.fromString(required(row, "assignment_id")),
                UUID.fromString(required(row, "volunteer_account_id")),
                required(row, "remarks"), Instant.parse(required(row, "completed_at")));
    }

    private static List<CompletionEvidence> mapEvidence(JsonNode rows) {
        if (!rows.isArray()) {
            throw new RepositoryException("AafatLink received invalid completion evidence.");
        }
        List<CompletionEvidence> evidence = new ArrayList<>();
        rows.forEach(row -> evidence.add(new CompletionEvidence(UUID.fromString(required(row, "id")),
                required(row, "original_filename"), required(row, "content_type"),
                row.path("size_bytes").asLong())));
        return List.copyOf(evidence);
    }

    private static ClosureReview mapClosureReview(JsonNode row) {
        return new ClosureReview(UUID.fromString(required(row, "id")),
                UUID.fromString(required(row, "request_id")),
                UUID.fromString(required(row, "assignment_id")),
                UUID.fromString(required(row, "coordinator_account_id")),
                ClosureDecision.valueOf(required(row, "decision")), nullable(row, "reason"),
                Instant.parse(required(row, "created_at")));
    }

    private static ProgressUpdateResult progressResult(JsonNode result) {
        try {
            return new ProgressUpdateResult(UUID.fromString(required(result, "update_id")),
                    UUID.fromString(required(result, "assignment_id")),
                    AssignmentStatus.valueOf(required(result, "assignment_status")),
                    UUID.fromString(required(result, "request_id")),
                    RequestStatus.valueOf(required(result, "request_status")),
                    result.path("assignment_version").asLong(), result.path("request_version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid progress result.", exception);
        }
    }

    private static TaskCompletionResult completionResult(JsonNode result) {
        try {
            return new TaskCompletionResult(UUID.fromString(required(result, "completion_id")),
                    UUID.fromString(required(result, "assignment_id")),
                    AssignmentStatus.valueOf(required(result, "assignment_status")),
                    UUID.fromString(required(result, "request_id")),
                    RequestStatus.valueOf(required(result, "request_status")),
                    result.path("assignment_version").asLong(), result.path("request_version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid task-completion result.", exception);
        }
    }

    private static ClosureReviewResult closureResult(JsonNode result) {
        try {
            return new ClosureReviewResult(UUID.fromString(required(result, "review_id")),
                    UUID.fromString(required(result, "assignment_id")),
                    AssignmentStatus.valueOf(required(result, "assignment_status")),
                    UUID.fromString(required(result, "request_id")),
                    RequestStatus.valueOf(required(result, "request_status")),
                    result.path("assignment_version").asLong(), result.path("request_version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received an invalid closure-review result.", exception);
        }
    }

    private static VolunteerAssignment mapAssignment(JsonNode row) {
        try {
            return new VolunteerAssignment(UUID.fromString(required(row, "id")),
                    UUID.fromString(required(row, "request_id")),
                    UUID.fromString(required(row, "volunteer_account_id")),
                    AssignmentStatus.valueOf(required(row, "status")),
                    Instant.parse(required(row, "assigned_at")), null, null,
                    instantOrNull(row, "scheduled_start_at"), instantOrNull(row, "scheduled_end_at"),
                    row.path("version").asLong());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException("AafatLink received invalid assignment data.", exception);
        }
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

    private static void putInstant(ObjectNode body, String name, Instant value) {
        if (value == null) {
            body.putNull(name);
        } else {
            body.put(name, value.toString());
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

    @Override
    public boolean hasActiveAssignment(AuthSession session, UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        JsonNode rows = send(session, "/rest/v1/volunteer_assignments?volunteer_account_id=eq." + accountId
                + "&status=in.(PENDING_ACCEPTANCE,ACCEPTED,IN_PROGRESS,AWAITING_CLOSURE_REVIEW)"
                + "&select=id&limit=1", "GET", null);
        if (!rows.isArray()) {
            throw new RepositoryException("AafatLink received invalid assignment-conflict data.");
        }
        return !rows.isEmpty();
    }

    private static String nullable(JsonNode object, String name) {
        JsonNode value = object.get(name);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
