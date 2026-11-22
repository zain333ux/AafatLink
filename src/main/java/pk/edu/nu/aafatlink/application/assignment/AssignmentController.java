package pk.edu.nu.aafatlink.application.assignment;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.assignment.AssignmentCreationResult;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter;
import pk.edu.nu.aafatlink.domain.assignment.ReassignmentResult;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerCandidate;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;
import pk.edu.nu.aafatlink.repository.VolunteerRepository;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;

/** GRASP application controller for UC05 assignment and reassignment. */
public final class AssignmentController {
    private final WorkflowRepository workflows;
    private final VolunteerRepository volunteers;

    public AssignmentController(WorkflowRepository workflows, VolunteerRepository volunteers) {
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.volunteers = Objects.requireNonNull(volunteers, "volunteers");
    }

    public List<AssistanceRequest> getAssignableRequests(AuthSession session) {
        return workflows.findAssignableRequests(Objects.requireNonNull(session, "session"));
    }

    public List<AssignmentContext> getActiveAssignments(AuthSession session) {
        return workflows.findActiveAssignmentsForCoordinator(Objects.requireNonNull(session, "session"));
    }

    public List<Skill> getSkills(AuthSession session) {
        return volunteers.findActiveSkills(Objects.requireNonNull(session, "session"));
    }

    public List<ServiceArea> getServiceAreas(AuthSession session) {
        return volunteers.findActiveServiceAreas(Objects.requireNonNull(session, "session"));
    }

    public List<VolunteerCandidate> findEligibleVolunteers(AuthSession session, AssistanceRequest request,
            AssignmentFilter filter) {
        requireAssignable(request);
        return volunteers.findEligible(Objects.requireNonNull(session, "session"), request,
                Objects.requireNonNull(filter, "filter"));
    }

    public AssignmentCreationResult assignVolunteer(AuthSession session, AssistanceRequest request,
            VolunteerCandidate candidate, AssignmentFilter filter) {
        requireAssignable(request);
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(filter, "filter");
        if (!candidate.profile().matchesRequirements(filter.skillId(), filter.serviceAreaId())) {
            throw new IllegalArgumentException("The volunteer no longer matches the selected requirements.");
        }
        return workflows.assignVolunteer(Objects.requireNonNull(session, "session"), UUID.randomUUID(), request,
                candidate.profile().accountId(), filter);
    }

    public ReassignmentResult reassignVolunteer(AuthSession session, UUID assignmentId, String reason) {
        Objects.requireNonNull(session, "session");
        var context = workflows.findAssignmentContext(session, Objects.requireNonNull(assignmentId, "assignmentId"))
                .orElseThrow(() -> new IllegalArgumentException("Assignment was not found."));
        long expectedVersion = context.assignment().version();
        context.assignment().markReassigned(reason);
        return workflows.reassignVolunteer(session, UUID.randomUUID(), assignmentId, expectedVersion,
                context.assignment().responseReason().orElseThrow());
    }

    private static void requireAssignable(AssistanceRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.canBeAssigned()) {
            throw new IllegalArgumentException("Only verified requests with a priority can be assigned.");
        }
    }
}
