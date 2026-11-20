package pk.edu.nu.aafatlink.domain.volunteer;

/** Predefined geographic area in which a volunteer is willing to serve. */
public record ServiceArea(long id, String name) {
    public ServiceArea {
        if (id <= 0) {
            throw new IllegalArgumentException("Service-area ID must be positive.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service-area name must not be blank.");
        }
        name = name.trim();
    }

    @Override
    public String toString() {
        return name;
    }
}
