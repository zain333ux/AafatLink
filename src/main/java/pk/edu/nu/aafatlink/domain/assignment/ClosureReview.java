package pk.edu.nu.aafatlink.domain.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable closure decision created by an authorized coordinator. */
public final class ClosureReview {
    private final UUID reviewId;
    private final UUID requestId;
    private final UUID assignmentId;
    private final UUID coordinatorAccountId;
    private final ClosureDecision decision;
    private final String remarks;
    private final Instant reviewedAt;

    public ClosureReview(UUID reviewId, UUID requestId, UUID assignmentId, UUID coordinatorAccountId,
            ClosureDecision decision, String remarks, Instant reviewedAt) {
        this.reviewId = Objects.requireNonNull(reviewId, "reviewId");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId");
        this.coordinatorAccountId = Objects.requireNonNull(coordinatorAccountId, "coordinatorAccountId");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.remarks = normalize(remarks);
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt");
        if (decision == ClosureDecision.RETURNED_FOR_REWORK && this.remarks == null) {
            throw new IllegalArgumentException("A reason is required when returning work for rework.");
        }
    }

    public UUID reviewId() { return reviewId; }
    public UUID requestId() { return requestId; }
    public UUID assignmentId() { return assignmentId; }
    public UUID coordinatorAccountId() { return coordinatorAccountId; }
    public ClosureDecision decision() { return decision; }
    public Optional<String> remarks() { return Optional.ofNullable(remarks); }
    public Instant reviewedAt() { return reviewedAt; }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
