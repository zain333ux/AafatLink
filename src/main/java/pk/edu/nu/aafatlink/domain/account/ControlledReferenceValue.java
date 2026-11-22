package pk.edu.nu.aafatlink.domain.account;

import java.util.Objects;

/** Versioned reference value that is deactivated rather than deleted from history. */
public record ControlledReferenceValue(ReferenceDataType type, String id, String name,
        boolean active, long version) {
    public ControlledReferenceValue {
        Objects.requireNonNull(type, "type");
        id = requireText(id, "id", 1, 100);
        name = requireText(name, "name", 2, 160);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    private static String requireText(String value, String field, int minimum, int maximum) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < minimum || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " must contain " + minimum + " to " + maximum + " characters");
        }
        return normalized;
    }
}
