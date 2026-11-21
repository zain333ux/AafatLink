package pk.edu.nu.aafatlink.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentCreationResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.assignment.ReassignmentResult;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReview;
import pk.edu.nu.aafatlink.domain.assignment.ClosureReviewResult;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdate;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdateResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletion;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletionResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.PriorityAssessmentResult;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestReviewResult;
import pk.edu.nu.aafatlink.domain.request.VerificationDecision;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;

/** Persistence boundary shared by the GRASP workflow controllers. */
public interface WorkflowRepository {
    boolean hasActiveAssignment(AuthSession session, UUID accountId);
    void beginRequestOperation(AuthSession session, UUID operationId, String operationType);

    void registerStagedEvidence(AuthSession session, UUID evidenceId, UUID operationId,
            String objectPath, EvidenceAttachment evidence);

    Optional<RequestSubmissionResult> reconcileRequestOperation(AuthSession session, UUID operationId);

    boolean abandonRequestOperation(AuthSession session, UUID operationId);

    RequestSubmissionResult submitRequest(AuthSession session, UUID operationId,
            UUID requesterId, AssistanceRequestDraft draft);

    RequestSubmissionResult resubmitRequest(AuthSession session, UUID operationId,
            UUID requestId, long expectedVersion, AssistanceRequestDraft draft);

    List<AssistanceRequest> findRequestsByRequester(AuthSession session, UUID requesterId);

    Optional<AssistanceRequest> findRequestForRequester(AuthSession session, UUID requestId, UUID requesterId);

    List<AssistanceRequest> findPendingVerificationRequests(AuthSession session);

    List<AssistanceRequest> findPrioritizableRequests(AuthSession session);

    Optional<AssistanceRequest> findRequestForReview(AuthSession session, UUID requestId);

    RequestReviewResult decideRequestVerification(AuthSession session, UUID operationId, UUID requestId,
            long expectedVersion, VerificationDecision decision, String reason);

    PriorityAssessmentResult recordPriorityAssessment(AuthSession session, UUID operationId, UUID requestId,
            long expectedVersion, RequestPriority suggested, RequestPriority selected, String justification);

    List<AssignmentContext> findPendingAssignments(AuthSession session, UUID volunteerId);

    Optional<AssignmentContext> findAssignmentContext(AuthSession session, UUID assignmentId);

    AssignmentResponseResult respondToAssignment(AuthSession session, UUID operationId, UUID assignmentId,
            long expectedVersion, boolean accept, String declineReason);

    List<AssistanceRequest> findAssignableRequests(AuthSession session);

    List<AssignmentContext> findActiveAssignmentsForCoordinator(AuthSession session);

    AssignmentCreationResult assignVolunteer(AuthSession session, UUID operationId, AssistanceRequest request,
            UUID volunteerId, AssignmentFilter filter);

    ReassignmentResult reassignVolunteer(AuthSession session, UUID operationId, UUID assignmentId,
            long expectedAssignmentVersion, String reason);

    List<TaskContext> findActiveTasks(AuthSession session, UUID volunteerId);

    Optional<TaskContext> findTaskContext(AuthSession session, UUID assignmentId);

    ProgressUpdateResult saveProgressAndStatus(AuthSession session, UUID operationId, ProgressUpdate update,
            UUID requestId, long expectedAssignmentVersion, long expectedRequestVersion);

    Optional<ProgressUpdateResult> reconcileProgressOperation(AuthSession session, UUID operationId);

    void beginTaskOperation(AuthSession session, UUID operationId, String operationType);

    TaskCompletionResult saveCompletionAndStatus(AuthSession session, UUID operationId,
            TaskCompletion completion, UUID requestId, long expectedAssignmentVersion, long expectedRequestVersion);

    Optional<TaskCompletionResult> reconcileTaskCompletion(AuthSession session, UUID operationId);

    boolean abandonTaskOperation(AuthSession session, UUID operationId);

    List<TaskContext> findRequestsAwaitingClosure(AuthSession session);

    Optional<TaskContext> findClosureContext(AuthSession session, UUID requestId);

    ClosureReviewResult saveClosureAndStatus(AuthSession session, UUID operationId, ClosureReview review,
            long expectedAssignmentVersion, long expectedRequestVersion);

    Optional<ClosureReviewResult> reconcileClosureOperation(AuthSession session, UUID operationId);
}
