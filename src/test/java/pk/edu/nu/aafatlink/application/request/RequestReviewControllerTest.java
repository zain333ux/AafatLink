package pk.edu.nu.aafatlink.application.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentStatus;
import pk.edu.nu.aafatlink.domain.assignment.ClosureDecision;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReview;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReviewResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletion;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerAssignment;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.PriorityAssessmentResult;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestReviewResult;
import pk.edu.nu.aafatlink.domain.request.RequestReviewDetails;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.VerificationDecision;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.service.request.PriorityAssessmentService;

class RequestReviewControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-20T01:00:00Z");
    private final AuthSession session = new AuthSession(UUID.randomUUID(), "coordinator@example.com",
            "access", "refresh", NOW.plusSeconds(3600));

    @Test
    void verifiesPendingRequestWithOptimisticVersion() {
        StubRepository repository = new StubRepository();
        RequestReviewController controller = controller(repository);
        AssistanceRequest request = request(RequestStatus.PENDING_VERIFICATION, 3);

        RequestReviewResult result = controller.verify(session, request);

        assertEquals(RequestStatus.VERIFIED, result.status());
        assertEquals(3, repository.expectedVersion);
        assertEquals(VerificationDecision.VERIFIED, repository.decision);
    }

    @Test
    void delegatesCompleteVerificationDetailsToTheWorkflowRepository() {
        StubRepository repository = new StubRepository();
        RequestReviewController controller = controller(repository);
        AssistanceRequest request = request(RequestStatus.PENDING_VERIFICATION, 1);
        repository.reviewDetails = new RequestReviewDetails(request, "Ayesha Khan", "+92 300 7654321",
                List.of(), List.of());

        RequestReviewDetails details = controller.getRequestReviewDetails(session, request.id()).orElseThrow();

        assertEquals("Ayesha Khan", details.requesterName());
    }

    @Test
    void rejectionAndReturnRequireRecordedReason() {
        StubRepository repository = new StubRepository();
        RequestReviewController controller = controller(repository);
        AssistanceRequest request = request(RequestStatus.PENDING_VERIFICATION, 0);

        assertThrows(IllegalArgumentException.class, () -> controller.reject(session, request, " "));
        controller.requestAdditionalInformation(session, request, "Provide a clearer location.");

        assertEquals(VerificationDecision.ADDITIONAL_INFORMATION_REQUIRED, repository.decision);
        assertEquals("Provide a clearer location.", repository.reason);
    }

    @Test
    void priorityOverridePassesSuggestionAndJustificationWithoutChangingStatus() {
        StubRepository repository = new StubRepository();
        RequestReviewController controller = controller(repository);
        AssistanceRequest request = request(RequestStatus.ASSIGNED, 4);

        PriorityAssessmentResult result = controller.setPriority(session, request, RequestPriority.CRITICAL,
                "Access roads have become unsafe.");

        assertEquals(RequestStatus.ASSIGNED, result.status());
        assertEquals(RequestPriority.MEDIUM, repository.suggested);
        assertEquals(RequestPriority.CRITICAL, repository.selected);
        assertEquals("Access roads have become unsafe.", repository.reason);
    }

    @Test
    void approvesClosureWithAtomicExpectedVersions() {
        TaskContext context = closureContext();
        ClosureRepository repository = new ClosureRepository(context);
        RequestReviewController controller = controller(repository);

        ClosureReviewResult result = controller.approveClosure(session, context, "Reviewed and confirmed.");

        assertEquals(RequestStatus.CLOSED, result.requestStatus());
        assertEquals(ClosureDecision.APPROVED, repository.review.decision());
        assertEquals(2, repository.assignmentVersion);
        assertEquals(7, repository.requestVersion);
    }

    @Test
    void returningClosureForReworkRequiresReason() {
        TaskContext context = closureContext();
        ClosureRepository repository = new ClosureRepository(context);
        RequestReviewController controller = controller(repository);

        assertThrows(IllegalArgumentException.class,
                () -> controller.returnForRework(session, context, " "));
    }

    private static RequestReviewController controller(WorkflowRepository repository) {
        return new RequestReviewController(repository, new PriorityAssessmentService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AssistanceRequest request(RequestStatus status, long version) {
        return new AssistanceRequest(UUID.randomUUID(), new AssistanceRequestDraft(DisasterType.FLOOD, null,
                "Sector G-10, Islamabad", "Roof damaged after rainfall.", "Temporary shelter",
                "One adult"), status, null, false, NOW.minusSeconds(3600), null, version, null, null, null);
    }

    private TaskContext closureContext() {
        UUID requestId = UUID.randomUUID();
        UUID volunteerId = UUID.randomUUID();
        VolunteerAssignment assignment = new VolunteerAssignment(UUID.randomUUID(), requestId, volunteerId,
                AssignmentStatus.AWAITING_CLOSURE_REVIEW, NOW.minusSeconds(7200), null, null, null, null, 2);
        AssistanceRequest request = request(RequestStatus.AWAITING_CLOSURE_REVIEW, 7);
        request = new AssistanceRequest(requestId, request.details(), request.status(), request.priority(),
                request.possibleDuplicate(), request.submittedAt(), request.resubmittedAt(), request.version(),
                null, null, null);
        TaskCompletion completion = new TaskCompletion(UUID.randomUUID(), assignment.id(), volunteerId,
                "Assistance delivered.", NOW.minusSeconds(300));
        return new TaskContext(new AssignmentContext(assignment, request), List.of(), completion, List.of(), null);
    }

    private static final class ClosureRepository
            extends pk.edu.nu.aafatlink.repository.WorkflowRepositoryStub {
        private final TaskContext context;
        private ClosureReview review;
        private long assignmentVersion;
        private long requestVersion;
        private ClosureRepository(TaskContext context) { this.context = context; }
        @Override public ClosureReviewResult saveClosureAndStatus(AuthSession session, UUID operationId,
                ClosureReview review, long assignmentVersion, long requestVersion) {
            this.review = review;
            this.assignmentVersion = assignmentVersion;
            this.requestVersion = requestVersion;
            boolean approved = review.decision() == ClosureDecision.APPROVED;
            return new ClosureReviewResult(review.reviewId(), review.assignmentId(),
                    approved ? AssignmentStatus.COMPLETED : AssignmentStatus.IN_PROGRESS,
                    review.requestId(), approved ? RequestStatus.CLOSED : RequestStatus.IN_PROGRESS,
                    assignmentVersion + 1, requestVersion + 1);
        }
        @Override public Optional<ClosureReviewResult> reconcileClosureOperation(AuthSession session,
                UUID operationId) { return Optional.empty(); }
    }

    private static final class StubRepository extends pk.edu.nu.aafatlink.repository.WorkflowRepositoryStub {
        private long expectedVersion;
        private VerificationDecision decision;
        private RequestPriority suggested;
        private RequestPriority selected;
        private String reason;
        private RequestReviewDetails reviewDetails;

        @Override public RequestReviewResult decideRequestVerification(AuthSession session, UUID operationId,
                UUID requestId, long expectedVersion, VerificationDecision decision, String reason) {
            this.expectedVersion = expectedVersion;
            this.decision = decision;
            this.reason = reason;
            return new RequestReviewResult(requestId, RequestStatus.valueOf(decision.name()), expectedVersion + 1);
        }

        @Override public PriorityAssessmentResult recordPriorityAssessment(AuthSession session, UUID operationId,
                UUID requestId, long expectedVersion, RequestPriority suggested, RequestPriority selected,
                String justification) {
            this.suggested = suggested;
            this.selected = selected;
            this.reason = justification;
            return new PriorityAssessmentResult(requestId, null, selected, RequestStatus.ASSIGNED,
                    expectedVersion + 1);
        }

        @Override public List<AssistanceRequest> findPendingVerificationRequests(AuthSession session) { return List.of(); }
        @Override public List<AssistanceRequest> findPrioritizableRequests(AuthSession session) { return List.of(); }
        @Override public Optional<AssistanceRequest> findRequestForReview(AuthSession session, UUID requestId) { return Optional.empty(); }
        @Override public Optional<RequestReviewDetails> findRequestReviewDetails(AuthSession session, UUID requestId) {
            return Optional.ofNullable(reviewDetails);
        }
        @Override public void beginRequestOperation(AuthSession session, UUID operationId, String operationType) { }
        @Override public void registerStagedEvidence(AuthSession session, UUID evidenceId, UUID operationId, String objectPath, EvidenceAttachment evidence) { }
        @Override public Optional<RequestSubmissionResult> reconcileRequestOperation(AuthSession session, UUID operationId) { return Optional.empty(); }
        @Override public boolean abandonRequestOperation(AuthSession session, UUID operationId) { return false; }
        @Override public RequestSubmissionResult submitRequest(AuthSession session, UUID operationId, UUID requesterId, AssistanceRequestDraft draft) { throw new UnsupportedOperationException(); }
        @Override public RequestSubmissionResult resubmitRequest(AuthSession session, UUID operationId, UUID requestId, long expectedVersion, AssistanceRequestDraft draft) { throw new UnsupportedOperationException(); }
        @Override public List<AssistanceRequest> findRequestsByRequester(AuthSession session, UUID requesterId) { return List.of(); }
        @Override public Optional<AssistanceRequest> findRequestForRequester(AuthSession session, UUID requestId, UUID requesterId) { return Optional.empty(); }
        @Override public List<AssignmentContext> findPendingAssignments(AuthSession session, UUID volunteerId) { return List.of(); }
        @Override public Optional<AssignmentContext> findAssignmentContext(AuthSession session, UUID assignmentId) { return Optional.empty(); }
        @Override public AssignmentResponseResult respondToAssignment(AuthSession session, UUID operationId, UUID assignmentId, long expectedVersion, boolean accept, String declineReason) { throw new UnsupportedOperationException(); }
        @Override public List<AssistanceRequest> findAssignableRequests(AuthSession session) { return List.of(); }
        @Override public List<AssignmentContext> findActiveAssignmentsForCoordinator(AuthSession session) { return List.of(); }
        @Override public pk.edu.nu.aafatlink.domain.assignment.AssignmentCreationResult assignVolunteer(AuthSession session, UUID operationId, AssistanceRequest request, UUID volunteerId, pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter filter) { throw new UnsupportedOperationException(); }
        @Override public pk.edu.nu.aafatlink.domain.assignment.ReassignmentResult reassignVolunteer(AuthSession session, UUID operationId, UUID assignmentId, long expectedAssignmentVersion, String reason) { throw new UnsupportedOperationException(); }
    }
}
