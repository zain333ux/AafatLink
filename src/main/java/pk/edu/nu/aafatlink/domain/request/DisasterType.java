package pk.edu.nu.aafatlink.domain.request;

/** Disaster categories defined by UC01. */
public enum DisasterType {
    FLOOD("Flood"),
    EARTHQUAKE("Earthquake"),
    FIRE("Fire"),
    LANDSLIDE("Landslide"),
    OTHER("Other");

    private final String displayName;

    DisasterType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String databaseValue() {
        return name().toLowerCase();
    }
}
