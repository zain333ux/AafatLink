package pk.edu.nu.aafatlink.domain.account;

import java.util.Objects;

/** Auditable create or update command for a controlled UC12 reference value. */
public record ReferenceDataChange(ReferenceDataType type, String existingId, String name,
        boolean active, long expectedVersion, String reason) {
    public ReferenceDataChange {
        Objects.requireNonNull(type, "type");
        existingId = existingId == null || existingId.isBlank() ? null : existingId.trim();
        name = name == null ? "" : name.trim();
        reason = reason == null ? "" : reason.trim();
        if (name.length() < 2 || name.length() > 160) {
            throw new IllegalArgumentException("Reference value must contain 2 to 160 characters.");
        }
        if (reason.length() < 5 || reason.length() > 500) {
            throw new IllegalArgumentException("Explain the reference-data change in 5 to 500 characters.");
        }
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        if (existingId == null && !type.allowsCreation()) {
            throw new IllegalArgumentException("Disaster types may be renamed or deactivated, but new codes require a reviewed application release.");
        }
        if (existingId == null && expectedVersion != 0) {
            throw new IllegalArgumentException("A new reference value must start at version zero.");
        }
    }
}
