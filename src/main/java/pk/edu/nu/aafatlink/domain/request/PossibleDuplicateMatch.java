package pk.edu.nu.aafatlink.domain.request;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Related open request surfaced to a Coordinator during UC03. */
public record PossibleDuplicateMatch(UUID requestId, RequestStatus status,
        RequestPriority priority, Instant submittedAt) {
    public PossibleDuplicateMatch {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }

    public Optional<RequestPriority> optionalPriority() {
        return Optional.ofNullable(priority);
    }
}
