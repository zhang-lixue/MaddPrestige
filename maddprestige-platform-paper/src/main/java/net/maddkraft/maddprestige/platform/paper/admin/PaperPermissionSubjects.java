package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.LinkedHashSet;
import java.util.Optional;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.AdministrationPermissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PaperPermissionSubjects {
    private PaperPermissionSubjects() {
    }

    public static PermissionSubject from(CommandSender sender) {
        LinkedHashSet<String> granted = new LinkedHashSet<>();
        AdministrationPermissions.all().stream().filter(sender::hasPermission).forEach(granted::add);
        Optional<java.util.UUID> uuid = sender instanceof Player player
                ? Optional.of(player.getUniqueId()) : Optional.empty();
        String type = sender instanceof Player ? "player" : "console";
        return new PermissionSubject(new Actor(type, uuid, sender.getName()), granted);
    }
}
