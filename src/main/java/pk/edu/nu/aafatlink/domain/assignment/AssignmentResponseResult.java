package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** Atomic UC08 result returned by the database workflow. */
public record AssignmentResponseResult(UUID assignmentId, AssignmentStatus assignmentStatus,
        UUID requestId, RequestStatus requestStatus, long assignmentVersion, long requestVersion) {
    public AssignmentResponseResult {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(assignmentStatus, "assignmentStatus");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestStatus, "requestStatus");
        if (assignmentVersion < 0 || requestVersion < 0) {
            throw new IllegalArgumentException("Workflow versions must not be negative.");
        }
    }
}
