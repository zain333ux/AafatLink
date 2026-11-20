package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** Atomic UC05 assignment result. */
public record AssignmentCreationResult(UUID assignmentId, UUID requestId,
        AssignmentStatus assignmentStatus, RequestStatus requestStatus, long requestVersion) {
    public AssignmentCreationResult {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(assignmentStatus, "assignmentStatus");
        Objects.requireNonNull(requestStatus, "requestStatus");
        if (requestVersion < 0) {
            throw new IllegalArgumentException("Request version must not be negative.");
        }
    }
}
