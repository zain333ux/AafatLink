package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** Result of returning an assigned request to the verified assignment queue. */
public record ReassignmentResult(UUID assignmentId, AssignmentStatus assignmentStatus,
        UUID requestId, RequestStatus requestStatus, long assignmentVersion, long requestVersion) {
    public ReassignmentResult {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(assignmentStatus, "assignmentStatus");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestStatus, "requestStatus");
    }
}
