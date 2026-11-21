package pk.edu.nu.aafatlink.application.request;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.assignment.ClosureDecision;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReview;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReviewResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.PriorityAssessmentResult;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestReviewResult;
import pk.edu.nu.aafatlink.domain.request.VerificationDecision;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.service.request.PriorityAssessmentService;

/** GRASP application controller for UC03 request verification and UC04 prioritization. */
public final class RequestReviewController {
    private final WorkflowRepository workflows;
    private final PriorityAssessmentService priorities;
    private final Clock clock;

    public RequestReviewController(WorkflowRepository workflows, PriorityAssessmentService priorities, Clock clock) {
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.priorities = Objects.requireNonNull(priorities, "priorities");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<AssistanceRequest> loadVerificationQueue(AuthSession session) {
        return workflows.findPendingVerificationRequests(Objects.requireNonNull(session, "session"));
    }

    public List<AssistanceRequest> loadPriorityQueue(AuthSession session) {
        return workflows.findPrioritizableRequests(Objects.requireNonNull(session, "session"));
    }

    public Optional<AssistanceRequest> getRequestForReview(AuthSession session, UUID requestId) {
        return workflows.findRequestForReview(Objects.requireNonNull(session, "session"),
                Objects.requireNonNull(requestId, "requestId"));
    }

    public RequestReviewResult verify(AuthSession session, AssistanceRequest request) {
        return decide(session, request, VerificationDecision.VERIFIED, null);
    }

    public RequestReviewResult reject(AuthSession session, AssistanceRequest request, String reason) {
        return decide(session, request, VerificationDecision.REJECTED, reason);
    }

    public RequestReviewResult requestAdditionalInformation(AuthSession session, AssistanceRequest request,
            String reason) {
        return decide(session, request, VerificationDecision.ADDITIONAL_INFORMATION_REQUIRED, reason);
    }

    public RequestPriority suggestPriority(AssistanceRequest request) {
        return priorities.suggestPriority(Objects.requireNonNull(request, "request"), clock.instant());
    }

    public PriorityAssessmentResult setPriority(AuthSession session, AssistanceRequest request,
            RequestPriority selected, String justification) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        if (!request.status().canBePrioritized()) {
            throw new IllegalArgumentException("This request cannot be prioritized in its current status.");
        }
        RequestPriority suggested = suggestPriority(request);
        String normalized = priorities.validateSelection(suggested,
                Objects.requireNonNull(selected, "selected"), justification);
        return workflows.recordPriorityAssessment(session, UUID.randomUUID(), request.id(), request.version(),
                suggested, selected, normalized);
    }

    public List<TaskContext> loadClosureQueue(AuthSession session) {
        return workflows.findRequestsAwaitingClosure(Objects.requireNonNull(session, "session"));
    }

    public Optional<TaskContext> getClosureDetails(AuthSession session, UUID requestId) {
        return workflows.findClosureContext(Objects.requireNonNull(session, "session"),
                Objects.requireNonNull(requestId, "requestId"));
    }

    public ClosureReviewResult approveClosure(AuthSession session, TaskContext context, String remarks) {
        return reviewClosure(session, context, ClosureDecision.APPROVED, remarks);
    }

    public ClosureReviewResult returnForRework(AuthSession session, TaskContext context, String reason) {
        return reviewClosure(session, context, ClosureDecision.RETURNED_FOR_REWORK, reason);
    }

    private ClosureReviewResult reviewClosure(AuthSession session, TaskContext context,
            ClosureDecision decision, String remarks) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(context, "context");
        if (!context.assignmentContext().request().canBeClosed()
                || !context.assignmentContext().assignment().canBeClosed()
                || context.completionDetails().isEmpty()) {
            throw new IllegalStateException("This request is no longer ready for closure review.");
        }
        ClosureReview review = new ClosureReview(UUID.randomUUID(),
                context.assignmentContext().request().id(), context.assignmentContext().assignment().id(),
                session.userId(), decision, remarks, clock.instant());
        if (decision == ClosureDecision.APPROVED) {
            context.assignmentContext().assignment().approveClosure();
        } else {
            context.assignmentContext().assignment().returnForRework(remarks);
        }
        UUID operationId = UUID.randomUUID();
        try {
            return workflows.saveClosureAndStatus(session, operationId, review,
                    context.assignmentContext().assignment().version(),
                    context.assignmentContext().request().version());
        } catch (RuntimeException failure) {
            Optional<ClosureReviewResult> committed = workflows.reconcileClosureOperation(session, operationId);
            if (committed.isPresent()) {
                return committed.get();
            }
            throw failure;
        }
    }

    private RequestReviewResult decide(AuthSession session, AssistanceRequest request,
            VerificationDecision decision, String reason) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        if (request.status() != pk.edu.nu.aafatlink.domain.request.RequestStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException("This request is no longer pending verification.");
        }
        String normalized = decision.validateReason(reason);
        return workflows.decideRequestVerification(session, UUID.randomUUID(), request.id(), request.version(),
                decision, normalized);
    }
}
