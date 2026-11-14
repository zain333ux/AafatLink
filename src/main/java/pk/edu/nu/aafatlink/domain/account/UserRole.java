package pk.edu.nu.aafatlink.domain.account;

/** Roles recognized by AafatLink authorization and navigation. */
public enum UserRole {
    REQUESTER("Requester"),
    VOLUNTEER("Volunteer"),
    COORDINATOR("Coordinator"),
    ADMINISTRATOR("Administrator");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
