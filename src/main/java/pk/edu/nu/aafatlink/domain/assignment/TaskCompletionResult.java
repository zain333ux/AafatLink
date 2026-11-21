package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** Atomic UC10 persistence result. */
public record TaskCompletionResult(UUID completionId, UUID assignmentId,
        AssignmentStatus assignmentStatus, UUID requestId, RequestStatus requestStatus,
        long assignmentVersion, long requestVersion) {
    public TaskCompletionResult {
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(assignmentStatus, "assignmentStatus");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestStatus, "requestStatus");
    }
}
