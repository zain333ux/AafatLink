package pk.edu.nu.aafatlink.domain.request;

import java.util.Objects;

/** Validated request data shared by new submissions and authorized resubmissions. */
public record AssistanceRequestDraft(
        DisasterType disasterType,
        String disasterTypeOther,
        String locationAddress,
        String description,
        String requiredAssistance,
        String affectedPersonDetails) {

    public AssistanceRequestDraft {
        disasterType = Objects.requireNonNull(disasterType, "disasterType");
        locationAddress = required(locationAddress, "Location", 5, 300);
        description = required(description, "Description", 10, 4000);
        requiredAssistance = required(requiredAssistance, "Required assistance", 2, 2000);
        affectedPersonDetails = required(affectedPersonDetails, "Affected-person details", 2, 2000);
        disasterTypeOther = optional(disasterTypeOther);
        if (disasterType == DisasterType.OTHER && disasterTypeOther == null) {
            throw new IllegalArgumentException("Specify the disaster type when Other is selected.");
        }
        if (disasterType != DisasterType.OTHER && disasterTypeOther != null) {
            throw new IllegalArgumentException("An Other specification is allowed only for Other.");
        }
        if (disasterTypeOther != null && (disasterTypeOther.length() < 2 || disasterTypeOther.length() > 80)) {
            throw new IllegalArgumentException("The disaster type specification must contain 2 to 80 characters.");
        }
    }

    private static String required(String value, String label, int minimum, int maximum) {
        String normalized = optional(value);
        if (normalized == null || normalized.length() < minimum || normalized.length() > maximum) {
            throw new IllegalArgumentException(label + " must contain " + minimum + " to " + maximum + " characters.");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
