package pk.edu.nu.aafatlink.application.volunteer;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentResponseResult;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdate;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdateResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletion;
import pk.edu.nu.aafatlink.domain.assignment.TaskCompletionResult;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerAssignment;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerProfile;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerReadinessChanges;
import pk.edu.nu.aafatlink.repository.VolunteerRepository;
import pk.edu.nu.aafatlink.repository.UserRepository;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.service.evidence.EvidenceStorage;

/** GRASP application controller for UC07 readiness and UC08 assignment response. */
public final class VolunteerController {
    private final VolunteerRepository volunteers;
    private final UserRepository users;
    private final WorkflowRepository workflows;
    private final Clock clock;
    private final EvidenceStorage evidenceStorage;

    public VolunteerController(VolunteerRepository volunteers, UserRepository users,
            WorkflowRepository workflows, Clock clock) {
        this(volunteers, users, workflows, clock, null);
    }

    public VolunteerController(VolunteerRepository volunteers, UserRepository users,
            WorkflowRepository workflows, Clock clock, EvidenceStorage evidenceStorage) {
        this.volunteers = Objects.requireNonNull(volunteers, "volunteers");
        this.users = Objects.requireNonNull(users, "users");
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.evidenceStorage = evidenceStorage;
    }

    public Optional<VolunteerProfile> getReadinessProfile(AuthSession session) {
        Objects.requireNonNull(session, "session");
        return volunteers.findProfile(session, session.userId())
                .map(profile -> profile.updateContactNo(users.findContactNumber(session, session.userId())));
    }

    public List<Skill> getSkills(AuthSession session) {
        return volunteers.findActiveSkills(Objects.requireNonNull(session, "session"));
    }

    public List<ServiceArea> getServiceAreas(AuthSession session) {
        return volunteers.findActiveServiceAreas(Objects.requireNonNull(session, "session"));
    }

    public List<String> getStandardDeclineReasons(AuthSession session) {
        return volunteers.findStandardDeclineReasons(Objects.requireNonNull(session, "session"));
    }

    public VolunteerProfile updateReadiness(AuthSession session, VolunteerReadinessChanges changes) {
        Objects.requireNonNull(session, "session");
        VolunteerProfile current = volunteers.findProfile(session, session.userId())
                .orElseThrow(() -> new IllegalStateException("Volunteer readiness profile was not found."));
        VolunteerProfile updated = current.applyReadinessChanges(Objects.requireNonNull(changes, "changes"),
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

    public List<TaskContext> getActiveTasks(AuthSession session) {
        Objects.requireNonNull(session, "session");
        return workflows.findActiveTasks(session, session.userId());
    }

    public Optional<TaskContext> getTaskDetails(AuthSession session, UUID assignmentId) {
        Objects.requireNonNull(session, "session");
        return workflows.findTaskContext(session, Objects.requireNonNull(assignmentId, "assignmentId"));
    }

    public ProgressUpdateResult recordProgress(AuthSession session, UUID assignmentId,
            String note, Integer percentage) {
        Objects.requireNonNull(session, "session");
        TaskContext context = requiredTask(session, assignmentId);
        ProgressUpdate update = context.assignmentContext().assignment()
                .recordProgress(session.userId(), note, percentage, clock.instant());
        UUID operationId = UUID.randomUUID();
        try {
            return workflows.saveProgressAndStatus(session, operationId, update,
                    context.assignmentContext().request().id(),
                    context.assignmentContext().assignment().version(),
                    context.assignmentContext().request().version());
        } catch (RuntimeException failure) {
            Optional<ProgressUpdateResult> committed = workflows.reconcileProgressOperation(session, operationId);
            if (committed.isPresent()) {
                return committed.get();
            }
            throw failure;
        }
    }

    public TaskCompletionResult completeTask(AuthSession session, UUID assignmentId, String remarks) {
        return completeTask(session, assignmentId, remarks, null);
    }

    public TaskCompletionResult completeTask(AuthSession session, UUID assignmentId, String remarks,
            EvidenceAttachment evidence) {
        Objects.requireNonNull(session, "session");
        TaskContext context = requiredTask(session, assignmentId);
        TaskCompletion completion = context.assignmentContext().assignment()
                .createCompletion(session.userId(), remarks, clock.instant());
        if (evidence != null) {
            completion.linkEvidence(evidence);
        }
        return executeCompletion(session, context, completion, evidence);
    }

    private TaskCompletionResult executeCompletion(AuthSession session, TaskContext context,
            TaskCompletion completion, EvidenceAttachment evidence) {
        if (evidence != null && evidenceStorage == null) {
            throw new IllegalStateException("Evidence storage is not configured.");
        }
        UUID operationId = UUID.randomUUID();
        String objectPath = null;
        try {
            workflows.beginTaskOperation(session, operationId, "COMPLETE_TASK");
            if (evidence != null) {
                objectPath = evidenceStorage.upload(session, operationId, evidence);
                workflows.registerStagedEvidence(session, UUID.randomUUID(), operationId, objectPath, evidence);
            }
            return workflows.saveCompletionAndStatus(session, operationId, completion,
                    context.assignmentContext().request().id(),
                    context.assignmentContext().assignment().version(),
                    context.assignmentContext().request().version());
        } catch (RuntimeException failure) {
            Optional<TaskCompletionResult> committed = workflows.reconcileTaskCompletion(session, operationId);
            if (committed.isPresent()) {
                return committed.get();
            }
            if (workflows.abandonTaskOperation(session, operationId) && objectPath != null) {
                evidenceStorage.delete(session, objectPath);
            }
            throw failure;
        }
    }

    private TaskContext requiredTask(AuthSession session, UUID assignmentId) {
        return workflows.findTaskContext(session, Objects.requireNonNull(assignmentId, "assignmentId"))
                .orElseThrow(() -> new IllegalArgumentException("The selected task is unavailable."));
    }
}
