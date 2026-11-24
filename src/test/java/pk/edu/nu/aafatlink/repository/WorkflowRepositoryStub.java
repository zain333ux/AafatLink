package pk.edu.nu.aafatlink.repository;

import pk.edu.nu.aafatlink.application.dashboard.DashboardEntry;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.assignment.*;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.*;
import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;

/** Test adapter whose subclasses override only the collaboration points under test. */
public abstract class WorkflowRepositoryStub implements WorkflowRepository {
    @Override public List<DashboardEntry> dashboard(AuthSession session, UserRole role) { throw unsupported(); }
    @Override public java.net.URI evidenceLink(AuthSession s, UUID id) { throw unsupported(); }
    @Override public List<String> reassignmentNotices(AuthSession s) { throw unsupported(); }
    protected UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    @Override public boolean hasActiveAssignment(AuthSession s, UUID a) { throw unsupported(); }
    @Override public void beginRequestOperation(AuthSession s, UUID o, String t) { throw unsupported(); }
    @Override public void registerStagedEvidence(AuthSession s, UUID e, UUID o, String p, EvidenceAttachment a) { throw unsupported(); }
    @Override public Optional<RequestSubmissionResult> reconcileRequestOperation(AuthSession s, UUID o) { throw unsupported(); }
    @Override public boolean abandonRequestOperation(AuthSession s, UUID o) { throw unsupported(); }
    @Override public RequestSubmissionResult submitRequest(AuthSession s, UUID o, UUID r, AssistanceRequestDraft d) { throw unsupported(); }
    @Override public RequestSubmissionResult recordCoordinatorRequest(AuthSession s, UUID o, RequesterContact c, AssistanceRequestDraft d) { throw unsupported(); }
    @Override public RequestSubmissionResult resubmitRequest(AuthSession s, UUID o, UUID r, long v, AssistanceRequestDraft d) { throw unsupported(); }
    @Override public List<AssistanceRequest> findRequestsByRequester(AuthSession s, UUID r) { throw unsupported(); }
    @Override public Optional<AssistanceRequest> findRequestForRequester(AuthSession s, UUID r, UUID u) { throw unsupported(); }
    @Override public List<AssistanceRequest> findPendingVerificationRequests(AuthSession s) { throw unsupported(); }
    @Override public List<AssistanceRequest> findPrioritizableRequests(AuthSession s) { throw unsupported(); }
    @Override public Optional<AssistanceRequest> findRequestForReview(AuthSession s, UUID r) { throw unsupported(); }
    @Override public Optional<RequestReviewDetails> findRequestReviewDetails(AuthSession s, UUID r) { throw unsupported(); }
    @Override public RequestReviewResult saveVerificationAndStatus(AuthSession s, UUID o, VerificationRecord r, long v) { throw unsupported(); }
    @Override public PriorityAssessmentResult savePriorityAndRequest(AuthSession s, UUID o, PriorityAssessment a, long v) { throw unsupported(); }
    @Override public List<AssignmentContext> findPendingAssignments(AuthSession s, UUID v) { throw unsupported(); }
    @Override public Optional<AssignmentContext> findAssignmentContext(AuthSession s, UUID a) { throw unsupported(); }
    @Override public AssignmentResponseResult respondToAssignment(AuthSession s, UUID o, UUID a, long v, boolean x, String r) { throw unsupported(); }
    @Override public List<AssistanceRequest> findAssignableRequests(AuthSession s) { throw unsupported(); }
    @Override public List<AssignmentContext> findActiveAssignmentsForCoordinator(AuthSession s) { throw unsupported(); }
    @Override public AssignmentCreationResult assignVolunteer(AuthSession s, UUID o, AssistanceRequest r, UUID v, AssignmentFilter f) { throw unsupported(); }
    @Override public ReassignmentResult reassignVolunteer(AuthSession s, UUID o, UUID a, long v, String r) { throw unsupported(); }
    @Override public List<TaskContext> findActiveTasks(AuthSession s, UUID v) { throw unsupported(); }
    @Override public Optional<TaskContext> findTaskContext(AuthSession s, UUID a) { throw unsupported(); }
    @Override public ProgressUpdateResult saveProgressAndStatus(AuthSession s, UUID o, ProgressUpdate u, UUID r, long a, long q) { throw unsupported(); }
    @Override public Optional<ProgressUpdateResult> reconcileProgressOperation(AuthSession s, UUID o) { throw unsupported(); }
    @Override public void beginTaskOperation(AuthSession s, UUID o, String t) { throw unsupported(); }
    @Override public TaskCompletionResult saveCompletionAndStatus(AuthSession s, UUID o, TaskCompletion c, UUID r, long a, long q) { throw unsupported(); }
    @Override public Optional<TaskCompletionResult> reconcileTaskCompletion(AuthSession s, UUID o) { throw unsupported(); }
    @Override public boolean abandonTaskOperation(AuthSession s, UUID o) { throw unsupported(); }
    @Override public List<TaskContext> findRequestsAwaitingClosure(AuthSession s) { throw unsupported(); }
    @Override public Optional<TaskContext> findClosureContext(AuthSession s, UUID r) { throw unsupported(); }
    @Override public ClosureReviewResult saveClosureAndStatus(AuthSession s, UUID o, ClosureReview r, long a, long q) { throw unsupported(); }
    @Override public Optional<ClosureReviewResult> reconcileClosureOperation(AuthSession s, UUID o) { throw unsupported(); }
}
