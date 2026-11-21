package pk.edu.nu.aafatlink.application.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.PriorityAssessmentResult;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestReviewResult;
import pk.edu.nu.aafatlink.domain.request.VerificationDecision;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.repository.RepositoryException;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.service.evidence.EvidenceStorage;

class RequestControllerTest {
    private final UUID userId = UUID.randomUUID();
    private final AuthSession session = new AuthSession(userId, "requester@example.com", "access", "refresh",
            Instant.now().plusSeconds(3600));
    private final AssistanceRequestDraft draft = new AssistanceRequestDraft(DisasterType.FLOOD, null,
            "Sector G-10, Islamabad", "Water entered the ground floor.",
            "Evacuation and drinking water", "Four adults and two children");

    @Test
    void submitsThroughWorkflowRepositoryWithAuthenticatedIdentity() {
        FakeWorkflowRepository repository = new FakeWorkflowRepository();
        RequestController controller = new RequestController(repository);

        RequestSubmissionResult result = controller.submitRequest(session, draft);

        assertEquals(userId, repository.requesterId);
        assertSame(draft, repository.submittedDraft);
        assertEquals(RequestStatus.PENDING_VERIFICATION, result.status());
    }

    @Test
    void requesterQueriesAreScopedByAuthenticatedIdentity() {
        FakeWorkflowRepository repository = new FakeWorkflowRepository();
        RequestController controller = new RequestController(repository);

        controller.getMyRequests(session);
        controller.getRequestDetails(session, UUID.randomUUID());

        assertEquals(userId, repository.requesterId);
    }

    @Test
    void resubmissionKeepsTheExistingRequestIdentity() {
        FakeWorkflowRepository repository = new FakeWorkflowRepository();
        RequestController controller = new RequestController(repository);
        UUID requestId = UUID.randomUUID();

        RequestSubmissionResult result = controller.resubmitRequest(session, requestId, 3, draft);

        assertEquals(requestId, result.requestId());
        assertEquals(requestId, repository.resubmittedRequestId);
    }

    @Test
    void committedEvidenceSubmissionIsRecoveredWithoutDeletingItsObject() {
        FakeWorkflowRepository repository = new FakeWorkflowRepository();
        FakeEvidenceStorage storage = new FakeEvidenceStorage();
        RequestController controller = new RequestController(repository, storage);
        EvidenceAttachment evidence = new EvidenceAttachment(java.nio.file.Path.of("evidence.pdf"),
                "application/pdf", 512);
        repository.failSubmission = true;
        repository.reconciledResult = new RequestSubmissionResult(
                UUID.randomUUID(), RequestStatus.PENDING_VERIFICATION, false, 1);

        RequestSubmissionResult result = controller.submitRequest(session, draft, evidence);

        assertSame(repository.reconciledResult, result);
        assertEquals(0, storage.deleteCount);
        assertEquals(repository.operationId, storage.operationId);
    }

    @Test
    void uncommittedEvidenceIsDeletedOnlyAfterOperationIsAbandoned() {
        FakeWorkflowRepository repository = new FakeWorkflowRepository();
        FakeEvidenceStorage storage = new FakeEvidenceStorage();
        RequestController controller = new RequestController(repository, storage);
        EvidenceAttachment evidence = new EvidenceAttachment(java.nio.file.Path.of("evidence.pdf"),
                "application/pdf", 512);
        repository.failSubmission = true;
        repository.abandoned = true;

        assertThrows(RepositoryException.class, () -> controller.submitRequest(session, draft, evidence));

        assertEquals(1, storage.deleteCount);
        assertEquals("request-evidence/" + userId + "/object.pdf", storage.deletedPath);
    }

    @Test
    void evidenceResubmissionKeepsExistingRequestIdentity() {
        FakeWorkflowRepository repository = new FakeWorkflowRepository();
        FakeEvidenceStorage storage = new FakeEvidenceStorage();
        RequestController controller = new RequestController(repository, storage);
        UUID requestId = UUID.randomUUID();
        EvidenceAttachment evidence = new EvidenceAttachment(java.nio.file.Path.of("evidence.pdf"),
                "application/pdf", 512);

        RequestSubmissionResult result = controller.resubmitRequest(session, requestId, 4, draft, evidence);

        assertEquals(requestId, result.requestId());
        assertEquals(requestId, repository.resubmittedRequestId);
    }

    private static final class FakeWorkflowRepository extends pk.edu.nu.aafatlink.repository.WorkflowRepositoryStub {
        private UUID requesterId;
        private AssistanceRequestDraft submittedDraft;
        private UUID resubmittedRequestId;
        private UUID operationId;
        private boolean failSubmission;
        private RequestSubmissionResult reconciledResult;
        private boolean abandoned;

        @Override
        public void beginRequestOperation(AuthSession session, UUID operationId, String operationType) {
            this.operationId = operationId;
        }

        @Override
        public void registerStagedEvidence(AuthSession session, UUID evidenceId, UUID operationId,
                String objectPath, EvidenceAttachment evidence) {
        }

        @Override
        public Optional<RequestSubmissionResult> reconcileRequestOperation(AuthSession session, UUID operationId) {
            return Optional.ofNullable(reconciledResult);
        }

        @Override
        public boolean abandonRequestOperation(AuthSession session, UUID operationId) {
            return abandoned;
        }

        @Override
        public RequestSubmissionResult submitRequest(AuthSession session, UUID operationId, UUID requesterId,
                AssistanceRequestDraft draft) {
            this.requesterId = requesterId;
            this.submittedDraft = draft;
            this.operationId = operationId;
            if (failSubmission) {
                throw new RepositoryException("Connection lost after submission.");
            }
            return new RequestSubmissionResult(UUID.randomUUID(), RequestStatus.PENDING_VERIFICATION, false, 0);
        }

        @Override
        public List<AssistanceRequest> findRequestsByRequester(AuthSession session, UUID requesterId) {
            this.requesterId = requesterId;
            return List.of();
        }

        @Override
        public RequestSubmissionResult resubmitRequest(AuthSession session, UUID operationId, UUID requestId,
                long expectedVersion, AssistanceRequestDraft draft) {
            resubmittedRequestId = requestId;
            return new RequestSubmissionResult(requestId, RequestStatus.PENDING_VERIFICATION, false, 0);
        }

        @Override
        public Optional<AssistanceRequest> findRequestForRequester(AuthSession session, UUID requestId,
                UUID requesterId) {
            this.requesterId = requesterId;
            return Optional.empty();
        }

        @Override
        public List<AssistanceRequest> findPendingVerificationRequests(AuthSession session) {
            return List.of();
        }

        @Override
        public List<AssistanceRequest> findPrioritizableRequests(AuthSession session) {
            return List.of();
        }

        @Override
        public Optional<AssistanceRequest> findRequestForReview(AuthSession session, UUID requestId) {
            return Optional.empty();
        }

        @Override public List<AssignmentContext> findPendingAssignments(AuthSession session, UUID volunteerId) { return List.of(); }
        @Override public Optional<AssignmentContext> findAssignmentContext(AuthSession session, UUID assignmentId) { return Optional.empty(); }
        @Override public AssignmentResponseResult respondToAssignment(AuthSession session, UUID operationId, UUID assignmentId, long expectedVersion, boolean accept, String declineReason) { throw new UnsupportedOperationException(); }
        @Override public List<AssistanceRequest> findAssignableRequests(AuthSession session) { return List.of(); }
        @Override public List<AssignmentContext> findActiveAssignmentsForCoordinator(AuthSession session) { return List.of(); }
        @Override public pk.edu.nu.aafatlink.domain.assignment.AssignmentCreationResult assignVolunteer(AuthSession session, UUID operationId, AssistanceRequest request, UUID volunteerId, pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter filter) { throw new UnsupportedOperationException(); }
        @Override public pk.edu.nu.aafatlink.domain.assignment.ReassignmentResult reassignVolunteer(AuthSession session, UUID operationId, UUID assignmentId, long expectedAssignmentVersion, String reason) { throw new UnsupportedOperationException(); }

        @Override
        public RequestReviewResult decideRequestVerification(AuthSession session, UUID operationId, UUID requestId,
                long expectedVersion, VerificationDecision decision, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PriorityAssessmentResult recordPriorityAssessment(AuthSession session, UUID operationId,
                UUID requestId, long expectedVersion, RequestPriority suggested, RequestPriority selected,
                String justification) {
            throw new UnsupportedOperationException();
        }
    }

    private final class FakeEvidenceStorage implements EvidenceStorage {
        private int deleteCount;
        private UUID operationId;
        private String deletedPath;

        @Override
        public String upload(AuthSession session, UUID operationId, EvidenceAttachment evidence) {
            this.operationId = operationId;
            return "request-evidence/" + userId + "/object.pdf";
        }

        @Override
        public void delete(AuthSession session, String objectPath) {
            deleteCount++;
            deletedPath = objectPath;
        }
    }
}
