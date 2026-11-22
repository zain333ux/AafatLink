package pk.edu.nu.aafatlink.domain.account;

/** Controlled reference catalogs maintained by an Administrator under UC12. */
public enum ReferenceDataType {
    DISASTER_TYPE("Disaster types", false),
    VOLUNTEER_SKILL("Volunteer skills", true),
    SERVICE_AREA("Service areas", true),
    DECLINE_REASON("Standard decline reasons", true);

    private final String displayName;
    private final boolean allowsCreation;

    ReferenceDataType(String displayName, boolean allowsCreation) {
        this.displayName = displayName;
        this.allowsCreation = allowsCreation;
    }

    public String displayName() { return displayName; }
    public boolean allowsCreation() { return allowsCreation; }
}
