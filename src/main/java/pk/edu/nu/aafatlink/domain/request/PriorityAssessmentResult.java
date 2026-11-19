package pk.edu.nu.aafatlink.domain.request;

import java.util.Objects;
import java.util.UUID;

public record PriorityAssessmentResult(UUID requestId, RequestPriority previousPriority,
        RequestPriority priority, RequestStatus status, long version) {
    public PriorityAssessmentResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }
}
