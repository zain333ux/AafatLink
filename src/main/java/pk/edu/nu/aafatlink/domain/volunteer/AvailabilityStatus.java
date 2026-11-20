package pk.edu.nu.aafatlink.domain.volunteer;

/** Volunteer-declared readiness used by UC07 and enforced again during UC05. */
public enum AvailabilityStatus {
    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable");

    private final String displayName;

    AvailabilityStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
