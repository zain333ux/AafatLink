package pk.edu.nu.aafatlink.domain.account;

/** A named authorization capability assigned to one or more roles. */
public record Permission(String permissionId, String permissionName) {
    public Permission {
        permissionId = requireText(permissionId, "permissionId");
        permissionName = requireText(permissionName, "permissionName");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
