package pk.edu.nu.aafatlink.domain.volunteer;

/** Administrator-controlled volunteer approval state. */
public enum VolunteerApprovalStatus {
    PENDING("Awaiting approval"),
    APPROVED("Approved"),
    REJECTED("Not approved");

    private final String displayName;

    VolunteerApprovalStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
