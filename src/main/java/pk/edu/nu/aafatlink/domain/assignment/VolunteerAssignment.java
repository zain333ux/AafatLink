package pk.edu.nu.aafatlink.domain.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Information Expert for assignment response and task-state rules. */
public final class VolunteerAssignment {
    private final UUID id;
    private final UUID requestId;
    private final UUID volunteerAccountId;
    private AssignmentStatus status;
    private final Instant assignedAt;
    private Instant respondedAt;
    private String responseReason;
    private final Instant scheduledStartAt;
    private final Instant scheduledEndAt;
    private final long version;

    public VolunteerAssignment(UUID id, UUID requestId, UUID volunteerAccountId,
            AssignmentStatus status, Instant assignedAt, Instant respondedAt, String responseReason,
            Instant scheduledStartAt, Instant scheduledEndAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.volunteerAccountId = Objects.requireNonNull(volunteerAccountId, "volunteerAccountId");
        this.status = Objects.requireNonNull(status, "status");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
        this.respondedAt = respondedAt;
        this.responseReason = normalizeOptional(responseReason);
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        if (version < 0) {
            throw new IllegalArgumentException("Assignment version must not be negative.");
        }
        this.version = version;
    }

    public boolean canRespond(UUID actorId) {
        return volunteerAccountId.equals(actorId) && status == AssignmentStatus.PENDING_ACCEPTANCE;
    }

    public void accept(UUID actorId, Instant responseTime) {
        ensureCanRespond(actorId);
        status = AssignmentStatus.ACCEPTED;
        respondedAt = Objects.requireNonNull(responseTime, "responseTime");
        responseReason = null;
    }

    public void decline(UUID actorId, String reason, Instant responseTime) {
        ensureCanRespond(actorId);
        String normalized = normalizeOptional(reason);
        if (normalized == null) {
            throw new IllegalArgumentException("Explain why the assignment is being declined.");
        }
        status = AssignmentStatus.DECLINED;
        respondedAt = Objects.requireNonNull(responseTime, "responseTime");
        responseReason = normalized;
    }

    public void markReassigned(String reason) {
        if (!status.canTransitionTo(AssignmentStatus.REASSIGNED)) {
            throw new IllegalStateException("This assignment can no longer be reassigned.");
        }
        String normalized = normalizeOptional(reason);
        if (normalized == null) {
            throw new IllegalArgumentException("A reassignment reason is required.");
        }
        status = AssignmentStatus.REASSIGNED;
        responseReason = normalized;
    }

    public boolean canRecordProgress(UUID actorId) {
        return volunteerAccountId.equals(actorId)
                && (status == AssignmentStatus.ACCEPTED || status == AssignmentStatus.IN_PROGRESS);
    }

    public boolean canCompleteTask(UUID actorId) {
        return canRecordProgress(actorId);
    }

    public boolean canBeClosed() {
        return status == AssignmentStatus.AWAITING_CLOSURE_REVIEW;
    }

    /** Creates an append-only update and advances accepted work to In Progress. */
    public ProgressUpdate recordProgress(UUID actorId, String note, Integer percent, Instant recordedAt) {
        if (!canRecordProgress(Objects.requireNonNull(actorId, "actorId"))) {
            throw new IllegalStateException("Only the assigned volunteer can update an active task.");
        }
        ProgressUpdate update = new ProgressUpdate(UUID.randomUUID(), id, actorId, note, percent,
                Objects.requireNonNull(recordedAt, "recordedAt"));
        status = AssignmentStatus.IN_PROGRESS;
        return update;
    }

    /** Creates the sole completion record and submits the task for coordinator review. */
    public TaskCompletion createCompletion(UUID actorId, String remarks, Instant completedAt) {
        if (!canCompleteTask(Objects.requireNonNull(actorId, "actorId"))) {
            throw new IllegalStateException("Only the assigned volunteer can complete an active task.");
        }
        TaskCompletion completion = new TaskCompletion(UUID.randomUUID(), id, actorId, remarks,
                Objects.requireNonNull(completedAt, "completedAt"));
        status = AssignmentStatus.AWAITING_CLOSURE_REVIEW;
        return completion;
    }

    public void approveClosure() {
        if (!canBeClosed()) {
            throw new IllegalStateException("This assignment is not awaiting closure review.");
        }
        status = AssignmentStatus.COMPLETED;
    }

    public void returnForRework(String reason) {
        if (!canBeClosed()) {
            throw new IllegalStateException("This assignment is not awaiting closure review.");
        }
        if (normalizeOptional(reason) == null) {
            throw new IllegalArgumentException("A reason is required when returning work for rework.");
        }
        status = AssignmentStatus.IN_PROGRESS;
        responseReason = reason.trim();
    }

    private void ensureCanRespond(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (!volunteerAccountId.equals(actorId)) {
            throw new IllegalArgumentException("Only the assigned volunteer can respond.");
        }
        if (status != AssignmentStatus.PENDING_ACCEPTANCE) {
            throw new IllegalStateException("This assignment is no longer awaiting a response.");
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public UUID requestId() { return requestId; }
    public UUID volunteerAccountId() { return volunteerAccountId; }
    public AssignmentStatus status() { return status; }
    public Instant assignedAt() { return assignedAt; }
    public Optional<Instant> respondedAt() { return Optional.ofNullable(respondedAt); }
    public Optional<String> responseReason() { return Optional.ofNullable(responseReason); }
    public Optional<Instant> scheduledStartAt() { return Optional.ofNullable(scheduledStartAt); }
    public Optional<Instant> scheduledEndAt() { return Optional.ofNullable(scheduledEndAt); }
    public long version() { return version; }
}
