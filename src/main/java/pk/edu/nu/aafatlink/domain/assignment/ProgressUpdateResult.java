package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** Atomic UC09 persistence result. */
public record ProgressUpdateResult(UUID updateId, UUID assignmentId, AssignmentStatus assignmentStatus,
        UUID requestId, RequestStatus requestStatus, long assignmentVersion, long requestVersion) {
    public ProgressUpdateResult {
        Objects.requireNonNull(updateId, "updateId");
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(assignmentStatus, "assignmentStatus");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestStatus, "requestStatus");
    }
}
