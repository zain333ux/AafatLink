package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** Atomic UC06 persistence result. */
public record ClosureReviewResult(UUID reviewId, UUID assignmentId,
        AssignmentStatus assignmentStatus, UUID requestId, RequestStatus requestStatus,
        long assignmentVersion, long requestVersion) {
    public ClosureReviewResult {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(assignmentStatus, "assignmentStatus");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestStatus, "requestStatus");
    }
}
