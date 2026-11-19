package pk.edu.nu.aafatlink.domain.request;

import java.util.Objects;
import java.util.UUID;

public record RequestReviewResult(UUID requestId, RequestStatus status, long version) {
    public RequestReviewResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }
}
