package pk.edu.nu.aafatlink.domain.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable audit description for an access decision. Persistence is atomic with the account update. */
public record AccessChangeLog(UUID targetAccountId, UUID administratorAccountId,
        AccessChanges before, AccessChanges after, Instant createdAt) {
    public AccessChangeLog {
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(administratorAccountId, "administratorAccountId");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
