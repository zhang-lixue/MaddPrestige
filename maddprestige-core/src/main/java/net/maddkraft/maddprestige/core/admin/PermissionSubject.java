package net.maddkraft.maddprestige.core.admin;

import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.operation.Actor;

public record PermissionSubject(Actor actor, Set<String> permissions) {
    public PermissionSubject {
        actor = Objects.requireNonNull(actor, "actor");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public boolean has(String permission) {
        Objects.requireNonNull(permission, "permission");
        if (permissions.contains(permission) || permissions.contains("maddprestige.*")) {
            return true;
        }
        int separator = permission.lastIndexOf('.');
        while (separator > 0) {
            if (permissions.contains(permission.substring(0, separator) + ".*")) {
                return true;
            }
            separator = permission.lastIndexOf('.', separator - 1);
        }
        return false;
    }

    public void require(String permission) {
        if (!has(permission)) {
            throw new AdministrationException(
                    "permission.denied", "Permission required: " + permission,
                    "Ask an owner to grant only the required MaddPrestige permission.");
        }
    }
}
