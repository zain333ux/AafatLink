package pk.edu.nu.aafatlink.domain.request;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable UC03 decision record associated with a request and reviewing coordinator. */
public record VerificationRecord(UUID verificationId, UUID requestId, UUID reviewerId,
        VerificationDecision outcome, String reason, Instant reviewedAt) {
    public VerificationRecord {
        Objects.requireNonNull(verificationId, "verificationId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
        reason = outcome.validateReason(reason);
    }
}
