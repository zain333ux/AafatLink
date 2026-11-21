package pk.edu.nu.aafatlink.domain.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable, append-only UC09 progress entry. */
public record ProgressUpdate(
        UUID updateId,
        UUID assignmentId,
        UUID volunteerAccountId,
        String progressNote,
        Integer completionPercent,
        Instant recordedAt) {
    public ProgressUpdate {
        Objects.requireNonNull(updateId, "updateId");
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(volunteerAccountId, "volunteerAccountId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        progressNote = normalize(progressNote);
        if (progressNote.length() > 500) {
            throw new IllegalArgumentException("Progress note must not exceed 500 characters.");
        }
        if (completionPercent != null && (completionPercent < 0 || completionPercent > 99)) {
            throw new IllegalArgumentException("Estimated completion must be a whole number from 0 to 99.");
        }
    }

    public Optional<Integer> percentage() {
        return Optional.ofNullable(completionPercent);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A progress note is required.");
        }
        return value.trim();
    }
}
