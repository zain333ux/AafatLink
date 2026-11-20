package pk.edu.nu.aafatlink.application.volunteer;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerAssignment;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerProfile;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerReadinessChanges;
import pk.edu.nu.aafatlink.repository.VolunteerRepository;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;

/** GRASP application controller for UC07 readiness and UC08 assignment response. */
public final class VolunteerController {
    private final VolunteerRepository volunteers;
    private final WorkflowRepository workflows;
    private final Clock clock;

    public VolunteerController(VolunteerRepository volunteers, WorkflowRepository workflows, Clock clock) {
        this.volunteers = Objects.requireNonNull(volunteers, "volunteers");
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<VolunteerProfile> getReadinessProfile(AuthSession session) {
        Objects.requireNonNull(session, "session");
        return volunteers.findProfile(session, session.userId());
    }

    public List<Skill> getSkills(AuthSession session) {
        return volunteers.findActiveSkills(Objects.requireNonNull(session, "session"));
    }

    public List<ServiceArea> getServiceAreas(AuthSession session) {
        return volunteers.findActiveServiceAreas(Objects.requireNonNull(session, "session"));
    }

    public VolunteerProfile updateReadiness(AuthSession session, VolunteerReadinessChanges changes) {
        Objects.requireNonNull(session, "session");
        VolunteerProfile current = volunteers.findProfile(session, session.userId())
                .orElseThrow(() -> new IllegalStateException("Volunteer readiness profile was not found."));
        VolunteerProfile updated = current.withReadinessChanges(Objects.requireNonNull(changes, "changes"),
                volunteers.findActiveSkills(session), volunteers.findActiveServiceAreas(session));
        return volunteers.saveReadinessAndContact(session, updated, current.version());
    }

    public List<AssignmentContext> getPendingAssignments(AuthSession session) {
        Objects.requireNonNull(session, "session");
        return workflows.findPendingAssignments(session, session.userId());
    }

    public Optional<AssignmentContext> getAssignmentDetails(AuthSession session, UUID assignmentId) {
        return workflows.findAssignmentContext(Objects.requireNonNull(session, "session"),
                Objects.requireNonNull(assignmentId, "assignmentId"));
    }

    public AssignmentResponseResult respondToAssignment(AuthSession session, UUID assignmentId,
            boolean accept, String reason) {
        Objects.requireNonNull(session, "session");
        AssignmentContext context = workflows.findAssignmentContext(session,
                Objects.requireNonNull(assignmentId, "assignmentId"))
                .orElseThrow(() -> new IllegalArgumentException("Assignment was not found."));
        VolunteerAssignment assignment = context.assignment();
        long expectedVersion = assignment.version();
        if (accept) {
            assignment.accept(session.userId(), clock.instant());
        } else {
            assignment.decline(session.userId(), reason, clock.instant());
        }
        return workflows.respondToAssignment(session, UUID.randomUUID(), assignment.id(), expectedVersion,
                accept, assignment.responseReason().orElse(null));
    }
}
