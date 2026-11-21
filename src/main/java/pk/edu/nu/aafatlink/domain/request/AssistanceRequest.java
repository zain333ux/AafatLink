package pk.edu.nu.aafatlink.domain.request;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Request information available to its owner for UC02 tracking. */
public record AssistanceRequest(
        UUID id,
        AssistanceRequestDraft details,
        RequestStatus status,
        RequestPriority priority,
        boolean possibleDuplicate,
        Instant submittedAt,
        Instant resubmittedAt,
        long version,
        String coordinatorReason,
        String assignmentState,
        String latestProgress) {
    public AssistanceRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** Whether UC06 may review this request for final closure. */
    public boolean canBeClosed() {
        return status == RequestStatus.AWAITING_CLOSURE_REVIEW;
    }
}
