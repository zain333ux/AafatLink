package pk.edu.nu.aafatlink.domain.volunteer;

import java.time.Instant;
import java.util.Set;

/** Validated UC07 input. Typed fields replace the diagram's unstructured changes map. */
public record VolunteerReadinessChanges(
        String contactNumber,
        AvailabilityStatus availability,
        String availabilityNotes,
        Instant availableFrom,
        Instant availableUntil,
        Set<Long> skillIds,
        Set<Long> serviceAreaIds) {
    public VolunteerReadinessChanges {
        contactNumber = normalizeContact(contactNumber);
        if (availability == null) {
            throw new IllegalArgumentException("Select an availability status.");
        }
        availabilityNotes = normalizeOptional(availabilityNotes, 500, "Availability notes");
        if ((availableFrom == null) != (availableUntil == null)) {
            throw new IllegalArgumentException("Provide both availability dates or leave both empty.");
        }
        if (availableFrom != null && !availableFrom.isBefore(availableUntil)) {
            throw new IllegalArgumentException("Availability end must be after its start.");
        }
        skillIds = Set.copyOf(skillIds == null ? Set.of() : skillIds);
        serviceAreaIds = Set.copyOf(serviceAreaIds == null ? Set.of() : serviceAreaIds);
        if (skillIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one response skill.");
        }
        if (serviceAreaIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one service area.");
        }
        if (skillIds.stream().anyMatch(id -> id == null || id <= 0)
                || serviceAreaIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Readiness selections contain an invalid identifier.");
        }
    }

    private static String normalizeContact(String value) {
        if (value == null || !value.trim().matches("^[+0-9][0-9 +()-]{6,24}$")) {
            throw new IllegalArgumentException("Enter a valid contact number (7 to 25 characters).");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value, int maximum, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(label + " must be " + maximum + " characters or fewer.");
        }
        return normalized;
    }
}
