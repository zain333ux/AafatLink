package pk.edu.nu.aafatlink.domain.volunteer;

/** Predefined response skill selectable in volunteer readiness profiles. */
public record Skill(long id, String name) {
    public Skill {
        if (id <= 0) {
            throw new IllegalArgumentException("Skill ID must be positive.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be blank.");
        }
        name = name.trim();
    }

    @Override
    public String toString() {
        return name;
    }
}
