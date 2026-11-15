package pk.edu.nu.aafatlink.domain.request;

import java.util.Objects;
import java.util.UUID;

/** Committed outcome returned by an idempotent submission RPC. */
public record RequestSubmissionResult(UUID requestId, RequestStatus status,
        boolean possibleDuplicate, int evidenceCount) {
    public RequestSubmissionResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        if (evidenceCount < 0) {
            throw new IllegalArgumentException("evidenceCount must not be negative");
        }
    }
}
