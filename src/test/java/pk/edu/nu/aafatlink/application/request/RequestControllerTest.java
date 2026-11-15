package pk.edu.nu.aafatlink.application.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;

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

    private static final class FakeWorkflowRepository implements WorkflowRepository {
        private UUID requesterId;
        private AssistanceRequestDraft submittedDraft;
        private UUID resubmittedRequestId;

        @Override
        public RequestSubmissionResult submitRequest(AuthSession session, UUID operationId, UUID requesterId,
                AssistanceRequestDraft draft) {
            this.requesterId = requesterId;
            this.submittedDraft = draft;
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
    }
}
