package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;

import pk.edu.nu.aafatlink.domain.volunteer.VolunteerProfile;

/** Eligible UC05 candidate with display and workload information. */
public record VolunteerCandidate(String displayName, VolunteerProfile profile, long activeAssignmentCount) {
    public VolunteerCandidate {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Candidate name must not be blank.");
        }
        displayName = displayName.trim();
        Objects.requireNonNull(profile, "profile");
        if (activeAssignmentCount < 0) {
            throw new IllegalArgumentException("Assignment workload must not be negative.");
        }
    }
}
