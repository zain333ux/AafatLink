package pk.edu.nu.aafatlink.domain.account;

import java.util.Objects;
import java.util.Set;

/** Role aggregate that groups the reusable permissions shown in the reviewed class diagram. */
public final class Role {
    private final UserRole roleId;
    private final String roleName;
    private final Set<Permission> permissions;

    public Role(UserRole roleId, String roleName, Set<Permission> permissions) {
        this.roleId = Objects.requireNonNull(roleId, "roleId");
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("roleName must not be blank");
        }
        this.roleName = roleName.trim();
        this.permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public UserRole roleId() { return roleId; }
    public String roleName() { return roleName; }
    public Set<Permission> permissions() { return permissions; }

    public boolean grants(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        return permissions.stream().anyMatch(granted -> granted.permissionId().equals(permission.permissionId()));
    }
}
