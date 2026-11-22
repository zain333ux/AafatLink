package pk.edu.nu.aafatlink.application.volunteer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentStatus;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdate;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdateResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletion;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletionResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerAssignment;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.PriorityAssessmentResult;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestReviewResult;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.VerificationDecision;
import pk.edu.nu.aafatlink.domain.volunteer.AvailabilityStatus;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerProfile;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerReadinessChanges;
import pk.edu.nu.aafatlink.repository.UserRepository;
import pk.edu.nu.aafatlink.repository.VolunteerRepository;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;

class VolunteerControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");
    private static final Skill FIRST_AID = new Skill(1, "First Aid");
    private static final ServiceArea ISLAMABAD = new ServiceArea(10, "Islamabad");
    private final UUID volunteerId = UUID.randomUUID();
    private final AuthSession session = new AuthSession(volunteerId, "volunteer@example.com",
            "access", "refresh", NOW.plusSeconds(3600));

    @Test
    void updatesReadinessThroughVolunteerRepositoryWithOptimisticVersion() {
        FakeVolunteerRepository volunteers = new FakeVolunteerRepository(profile());
        VolunteerController controller = controller(volunteers, new FakeWorkflowRepository(null));
        VolunteerReadinessChanges changes = new VolunteerReadinessChanges("+92 333 7654321",
                AvailabilityStatus.AVAILABLE, "Available today", null, null, Set.of(1L), Set.of(10L));

        VolunteerProfile saved = controller.updateReadiness(session, changes);

        assertEquals(4, volunteers.expectedVersion);
        assertEquals(AvailabilityStatus.AVAILABLE, saved.availability());
        assertEquals("+92 333 7654321", saved.contactNumber());
    }

    @Test
    void combinesVolunteerProfileWithContactFromUserRepository() {
        FakeVolunteerRepository volunteers = new FakeVolunteerRepository(profile());
        VolunteerController controller = controller(volunteers, new FakeWorkflowRepository(null));

        VolunteerProfile loaded = controller.getReadinessProfile(session).orElseThrow();

        assertEquals("+92 311 7654321", loaded.contactNumber());
    }

    @Test
    void exposesAdministratorControlledDeclineReasons() {
        VolunteerController controller = controller(new FakeVolunteerRepository(profile()),
                new FakeWorkflowRepository(null));

        assertEquals(List.of("Conflicting emergency assignment"),
                controller.getStandardDeclineReasons(session));
    }

    @Test
    void acceptsOwnedPendingAssignmentUsingItsVersion() {
        AssignmentContext context = assignmentContext();
        FakeWorkflowRepository workflows = new FakeWorkflowRepository(context);
        VolunteerController controller = controller(new FakeVolunteerRepository(profile()), workflows);

        AssignmentResponseResult result = controller.respondToAssignment(session,
                context.assignment().id(), true, null);

        assertEquals(AssignmentStatus.ACCEPTED, result.assignmentStatus());
        assertEquals(2, workflows.expectedVersion);
        assertEquals(true, workflows.accepted);
    }

    @Test
    void declineRequiresReasonBeforeRepositoryWrite() {
        AssignmentContext context = assignmentContext();
        FakeWorkflowRepository workflows = new FakeWorkflowRepository(context);
        VolunteerController controller = controller(new FakeVolunteerRepository(profile()), workflows);

        assertThrows(IllegalArgumentException.class,
                () -> controller.respondToAssignment(session, context.assignment().id(), false, " "));
        assertEquals(0, workflows.responseWrites);
    }

    @Test
    void recordsProgressWithDocumentedVersionsAndTransition() {
        TaskWorkflowRepository workflows = new TaskWorkflowRepository(taskContext(AssignmentStatus.ACCEPTED));
        VolunteerController controller = controller(new FakeVolunteerRepository(profile()), workflows);

        ProgressUpdateResult result = controller.recordProgress(session,
                workflows.context.assignmentContext().assignment().id(), "Reached the affected family.", 25);

        assertEquals(AssignmentStatus.IN_PROGRESS, result.assignmentStatus());
        assertEquals(2, workflows.expectedAssignmentVersion);
        assertEquals(5, workflows.expectedRequestVersion);
        assertEquals("Reached the affected family.", workflows.progress.progressNote());
    }

    @Test
    void completesAcceptedTaskWithoutRequiringEvidence() {
        TaskWorkflowRepository workflows = new TaskWorkflowRepository(taskContext(AssignmentStatus.ACCEPTED));
        VolunteerController controller = controller(new FakeVolunteerRepository(profile()), workflows);

        TaskCompletionResult result = controller.completeTask(session,
                workflows.context.assignmentContext().assignment().id(), "Shelter material delivered.");

        assertEquals(AssignmentStatus.AWAITING_CLOSURE_REVIEW, result.assignmentStatus());
        assertEquals("COMPLETE_TASK", workflows.operationType);
        assertEquals("Shelter material delivered.", workflows.completion.remarks());
    }

    private VolunteerController controller(VolunteerRepository volunteers, WorkflowRepository workflows) {
        return new VolunteerController(volunteers, new FakeUserRepository(), workflows,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private VolunteerProfile profile() {
        return new VolunteerProfile(volunteerId, VolunteerApprovalStatus.PENDING,
                AvailabilityStatus.UNAVAILABLE, "+92 300 1234567", null, null, null,
                List.of(FIRST_AID), List.of(ISLAMABAD), 4);
    }

    private AssignmentContext assignmentContext() {
        UUID requestId = UUID.randomUUID();
        VolunteerAssignment assignment = new VolunteerAssignment(UUID.randomUUID(), requestId, volunteerId,
                AssignmentStatus.PENDING_ACCEPTANCE, NOW.minusSeconds(300), null, null, null, null, 2);
        AssistanceRequest request = new AssistanceRequest(requestId,
                new AssistanceRequestDraft(DisasterType.FLOOD, null, "G-10, Islamabad",
                        "Family displaced by floodwater.", "Temporary shelter", "Two adults"),
                RequestStatus.ASSIGNED, RequestPriority.HIGH, false, NOW.minusSeconds(3600), null, 5,
                null, null, null);
        return new AssignmentContext(assignment, request);
    }

    private TaskContext taskContext(AssignmentStatus status) {
        UUID requestId = UUID.randomUUID();
        VolunteerAssignment assignment = new VolunteerAssignment(UUID.randomUUID(), requestId, volunteerId,
                status, NOW.minusSeconds(300), null, null, null, null, 2);
        AssistanceRequest request = new AssistanceRequest(requestId,
                new AssistanceRequestDraft(DisasterType.FLOOD, null, "G-10, Islamabad",
                        "Family displaced by floodwater.", "Temporary shelter", "Two adults"),
                status == AssignmentStatus.ACCEPTED ? RequestStatus.ASSIGNED : RequestStatus.IN_PROGRESS,
                RequestPriority.HIGH, false, NOW.minusSeconds(3600), null, 5, null, null, null);
        return new TaskContext(new AssignmentContext(assignment, request), List.of(), null, List.of(), null);
    }

    private static final class TaskWorkflowRepository
            extends pk.edu.nu.aafatlink.repository.WorkflowRepositoryStub {
        private final TaskContext context;
        private long expectedAssignmentVersion;
        private long expectedRequestVersion;
        private ProgressUpdate progress;
        private TaskCompletion completion;
        private String operationType;

        private TaskWorkflowRepository(TaskContext context) { this.context = context; }
        @Override public Optional<TaskContext> findTaskContext(AuthSession session, UUID assignmentId) {
            return Optional.of(context);
        }
        @Override public ProgressUpdateResult saveProgressAndStatus(AuthSession session, UUID operationId,
                ProgressUpdate update, UUID requestId, long assignmentVersion, long requestVersion) {
            progress = update;
            expectedAssignmentVersion = assignmentVersion;
            expectedRequestVersion = requestVersion;
            return new ProgressUpdateResult(update.updateId(), update.assignmentId(), AssignmentStatus.IN_PROGRESS,
                    requestId, RequestStatus.IN_PROGRESS, assignmentVersion + 1, requestVersion + 1);
        }
        @Override public Optional<ProgressUpdateResult> reconcileProgressOperation(AuthSession session,
                UUID operationId) { return Optional.empty(); }
        @Override public void beginTaskOperation(AuthSession session, UUID operationId, String operationType) {
            this.operationType = operationType;
        }
        @Override public TaskCompletionResult saveCompletionAndStatus(AuthSession session, UUID operationId,
                TaskCompletion completion, UUID requestId, long assignmentVersion, long requestVersion) {
            this.completion = completion;
            return new TaskCompletionResult(completion.completionId(), completion.assignmentId(),
                    AssignmentStatus.AWAITING_CLOSURE_REVIEW, requestId,
                    RequestStatus.AWAITING_CLOSURE_REVIEW, assignmentVersion + 1, requestVersion + 1);
        }
        @Override public Optional<TaskCompletionResult> reconcileTaskCompletion(AuthSession session,
                UUID operationId) { return Optional.empty(); }
        @Override public boolean abandonTaskOperation(AuthSession session, UUID operationId) { return false; }
    }

    private static final class FakeVolunteerRepository implements VolunteerRepository {
        private VolunteerProfile profile;
        private long expectedVersion;

        private FakeVolunteerRepository(VolunteerProfile profile) {
            this.profile = profile;
        }

        @Override public List<String> findStandardDeclineReasons(AuthSession session) {
            return List.of("Conflicting emergency assignment");
        }
        @Override public List<Skill> findActiveSkills(AuthSession session) { return List.of(FIRST_AID); }
        @Override public List<ServiceArea> findActiveServiceAreas(AuthSession session) { return List.of(ISLAMABAD); }
        @Override public Optional<VolunteerProfile> findProfile(AuthSession session, UUID volunteerId) {
            return Optional.of(profile);
        }
        @Override public VolunteerProfile saveReadinessAndContact(AuthSession session, VolunteerProfile profile,
                long expectedVersion) {
            this.expectedVersion = expectedVersion;
            this.profile = profile;
            return profile;
        }
        @Override public List<pk.edu.nu.aafatlink.domain.assignment.VolunteerCandidate> findEligible(
                AuthSession session, AssistanceRequest request,
                pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter filter) { return List.of(); }
    }

    private static final class FakeUserRepository implements UserRepository {
        @Override public String findContactNumber(AuthSession session, UUID accountId) {
            return "+92 311 7654321";
        }
        @Override public List<pk.edu.nu.aafatlink.domain.account.Role> findRoles(AuthSession session) {
            throw new UnsupportedOperationException();
        }
        @Override public List<pk.edu.nu.aafatlink.domain.account.ControlledReferenceValue> findReferenceValues(
                AuthSession session, pk.edu.nu.aafatlink.domain.account.ReferenceDataType type) {
            throw new UnsupportedOperationException();
        }
        @Override public pk.edu.nu.aafatlink.domain.account.ControlledReferenceValue saveReferenceValue(
                AuthSession session, UUID operationId,
                pk.edu.nu.aafatlink.domain.account.ReferenceDataChange change) {
            throw new UnsupportedOperationException();
        }
        @Override public List<pk.edu.nu.aafatlink.domain.account.UserAccount> findUsers(AuthSession session) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<pk.edu.nu.aafatlink.domain.account.UserAccount> findUser(
                AuthSession session, UUID accountId) { throw new UnsupportedOperationException(); }
        @Override public int countActiveAdministrators(AuthSession session) {
            throw new UnsupportedOperationException();
        }
        @Override public pk.edu.nu.aafatlink.domain.account.UserAccount saveAccessWithAudit(
                AuthSession session, UUID operationId,
                pk.edu.nu.aafatlink.domain.account.UserAccount account,
                pk.edu.nu.aafatlink.domain.account.AccessChanges changes, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeWorkflowRepository extends pk.edu.nu.aafatlink.repository.WorkflowRepositoryStub {
        private final AssignmentContext context;
        private long expectedVersion;
        private boolean accepted;
        private int responseWrites;

        private FakeWorkflowRepository(AssignmentContext context) {
            this.context = context;
        }

        @Override public List<AssignmentContext> findPendingAssignments(AuthSession session, UUID volunteerId) {
            return context == null ? List.of() : List.of(context);
        }
        @Override public Optional<AssignmentContext> findAssignmentContext(AuthSession session, UUID assignmentId) {
            return Optional.ofNullable(context);
        }
        @Override public AssignmentResponseResult respondToAssignment(AuthSession session, UUID operationId,
                UUID assignmentId, long expectedVersion, boolean accept, String declineReason) {
            this.expectedVersion = expectedVersion;
            this.accepted = accept;
            responseWrites++;
            return new AssignmentResponseResult(assignmentId,
                    accept ? AssignmentStatus.ACCEPTED : AssignmentStatus.DECLINED,
                    context.request().id(), accept ? RequestStatus.ASSIGNED : RequestStatus.VERIFIED,
                    expectedVersion + 1, context.request().version() + (accept ? 0 : 1));
        }

        @Override public void beginRequestOperation(AuthSession session, UUID operationId, String operationType) { }
        @Override public void registerStagedEvidence(AuthSession session, UUID evidenceId, UUID operationId, String objectPath, EvidenceAttachment evidence) { }
        @Override public Optional<RequestSubmissionResult> reconcileRequestOperation(AuthSession session, UUID operationId) { return Optional.empty(); }
        @Override public boolean abandonRequestOperation(AuthSession session, UUID operationId) { return false; }
        @Override public RequestSubmissionResult submitRequest(AuthSession session, UUID operationId, UUID requesterId, AssistanceRequestDraft draft) { throw new UnsupportedOperationException(); }
        @Override public RequestSubmissionResult resubmitRequest(AuthSession session, UUID operationId, UUID requestId, long expectedVersion, AssistanceRequestDraft draft) { throw new UnsupportedOperationException(); }
        @Override public List<AssistanceRequest> findRequestsByRequester(AuthSession session, UUID requesterId) { return List.of(); }
        @Override public Optional<AssistanceRequest> findRequestForRequester(AuthSession session, UUID requestId, UUID requesterId) { return Optional.empty(); }
        @Override public List<AssistanceRequest> findPendingVerificationRequests(AuthSession session) { return List.of(); }
        @Override public List<AssistanceRequest> findPrioritizableRequests(AuthSession session) { return List.of(); }
        @Override public Optional<AssistanceRequest> findRequestForReview(AuthSession session, UUID requestId) { return Optional.empty(); }
        @Override public RequestReviewResult saveVerificationAndStatus(AuthSession session, UUID operationId, pk.edu.nu.aafatlink.domain.request.VerificationRecord record, long expectedVersion) { throw new UnsupportedOperationException(); }
        @Override public PriorityAssessmentResult savePriorityAndRequest(AuthSession session, UUID operationId, pk.edu.nu.aafatlink.domain.request.PriorityAssessment assessment, long expectedVersion) { throw new UnsupportedOperationException(); }
        @Override public List<AssistanceRequest> findAssignableRequests(AuthSession session) { return List.of(); }
        @Override public List<AssignmentContext> findActiveAssignmentsForCoordinator(AuthSession session) { return List.of(); }
        @Override public pk.edu.nu.aafatlink.domain.assignment.AssignmentCreationResult assignVolunteer(AuthSession session, UUID operationId, AssistanceRequest request, UUID volunteerId, pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter filter) { throw new UnsupportedOperationException(); }
        @Override public pk.edu.nu.aafatlink.domain.assignment.ReassignmentResult reassignVolunteer(AuthSession session, UUID operationId, UUID assignmentId, long expectedAssignmentVersion, String reason) { throw new UnsupportedOperationException(); }
    }
}
