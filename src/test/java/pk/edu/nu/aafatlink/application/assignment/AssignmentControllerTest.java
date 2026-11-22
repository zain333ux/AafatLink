package pk.edu.nu.aafatlink.application.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentCreationResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentStatus;
import pk.edu.nu.aafatlink.domain.assignment.ReassignmentResult;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerCandidate;
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
import pk.edu.nu.aafatlink.repository.VolunteerRepository;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;

class AssignmentControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-21T09:00:00Z");
    private static final Skill FIRST_AID = new Skill(1, "First Aid");
    private static final ServiceArea ISLAMABAD = new ServiceArea(10, "Islamabad - Central");
    private final AuthSession session = new AuthSession(UUID.randomUUID(), "coordinator@example.com",
            "access", "refresh", NOW.plusSeconds(3600));

    @Test
    void findsAndAssignsEligibleVolunteerWithRequestVersion() {
        AssistanceRequest request = request(RequestStatus.VERIFIED, RequestPriority.HIGH);
        VolunteerCandidate candidate = candidate();
        FakeVolunteerRepository volunteers = new FakeVolunteerRepository(candidate);
        FakeWorkflowRepository workflows = new FakeWorkflowRepository(request);
        AssignmentController controller = new AssignmentController(workflows, volunteers);
        AssignmentFilter filter = new AssignmentFilter(1, 10, NOW.plusSeconds(3600), NOW.plusSeconds(7200));

        assertEquals(List.of(candidate), controller.findEligibleVolunteers(session, request, filter));
        AssignmentCreationResult result = controller.assignVolunteer(session, request, candidate, filter);

        assertEquals(AssignmentStatus.PENDING_ACCEPTANCE, result.assignmentStatus());
        assertEquals(RequestStatus.ASSIGNED, result.requestStatus());
        assertEquals(request.version(), workflows.expectedRequestVersion);
    }

    @Test
    void refusesRequestWithoutVerifiedPriority() {
        AssignmentController controller = new AssignmentController(new FakeWorkflowRepository(null),
                new FakeVolunteerRepository(candidate()));

        assertThrows(IllegalArgumentException.class, () -> controller.findEligibleVolunteers(session,
                request(RequestStatus.PENDING_VERIFICATION, null), new AssignmentFilter(1, 10, null, null)));
    }

    private static AssistanceRequest request(RequestStatus status, RequestPriority priority) {
        return new AssistanceRequest(UUID.randomUUID(),
                new AssistanceRequestDraft(DisasterType.FLOOD, null, "G-10, Islamabad",
                        "Family displaced after flash flooding.", "First aid and shelter", "Two adults"),
                status, priority, false, NOW.minusSeconds(3600), null, 6, null, null, null);
    }

    private static VolunteerCandidate candidate() {
        VolunteerProfile profile = new VolunteerProfile(UUID.randomUUID(), VolunteerApprovalStatus.APPROVED,
                AvailabilityStatus.AVAILABLE, "+92 300 1234567", null, null, null,
                List.of(FIRST_AID), List.of(ISLAMABAD), 2);
        return new VolunteerCandidate("Ayesha Khan", profile, 0);
    }

    private static final class FakeVolunteerRepository implements VolunteerRepository {
        private final VolunteerCandidate candidate;
        private FakeVolunteerRepository(VolunteerCandidate candidate) { this.candidate = candidate; }
        @Override public List<Skill> findActiveSkills(AuthSession session) { return List.of(FIRST_AID); }
        @Override public List<ServiceArea> findActiveServiceAreas(AuthSession session) { return List.of(ISLAMABAD); }
        @Override public Optional<VolunteerProfile> findProfile(AuthSession session, UUID volunteerId) { return Optional.empty(); }
        @Override public VolunteerProfile saveReadinessAndContact(AuthSession session, VolunteerProfile profile, long expectedVersion) { throw new UnsupportedOperationException(); }
        @Override public List<VolunteerCandidate> findEligible(AuthSession session, AssistanceRequest request, AssignmentFilter filter) { return List.of(candidate); }
    }

    private static final class FakeWorkflowRepository extends pk.edu.nu.aafatlink.repository.WorkflowRepositoryStub {
        private final AssistanceRequest request;
        private long expectedRequestVersion;
        private FakeWorkflowRepository(AssistanceRequest request) { this.request = request; }
        @Override public List<AssistanceRequest> findAssignableRequests(AuthSession session) { return request == null ? List.of() : List.of(request); }
        @Override public List<AssignmentContext> findActiveAssignmentsForCoordinator(AuthSession session) { return List.of(); }
        @Override public AssignmentCreationResult assignVolunteer(AuthSession session, UUID operationId,
                AssistanceRequest request, UUID volunteerId, AssignmentFilter filter) {
            expectedRequestVersion = request.version();
            return new AssignmentCreationResult(UUID.randomUUID(), request.id(),
                    AssignmentStatus.PENDING_ACCEPTANCE, RequestStatus.ASSIGNED, request.version() + 1);
        }
        @Override public ReassignmentResult reassignVolunteer(AuthSession session, UUID operationId, UUID assignmentId, long expectedAssignmentVersion, String reason) { throw new UnsupportedOperationException(); }
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
        @Override public List<AssignmentContext> findPendingAssignments(AuthSession session, UUID volunteerId) { return List.of(); }
        @Override public Optional<AssignmentContext> findAssignmentContext(AuthSession session, UUID assignmentId) { return Optional.empty(); }
        @Override public AssignmentResponseResult respondToAssignment(AuthSession session, UUID operationId, UUID assignmentId, long expectedVersion, boolean accept, String declineReason) { throw new UnsupportedOperationException(); }
    }
}
