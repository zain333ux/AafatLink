package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;
import java.util.UUID;

/** Safe metadata for committed completion evidence displayed during UC06. */
public record CompletionEvidence(UUID evidenceId, String fileName, String contentType, long sizeBytes) {
    public CompletionEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId");
        if (fileName == null || fileName.isBlank() || contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Completion evidence metadata is incomplete.");
        }
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("Completion evidence size must be positive.");
        }
    }
}
