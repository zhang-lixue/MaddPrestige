package gg.maddkraft.prestige.util;

import gg.maddkraft.prestige.config.PluginSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Locale;

public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##");

    private Text() {
    }

    public static Component mm(String input) {
        try {
            return MINI.deserialize(input == null ? "" : input);
        } catch (RuntimeException ignored) {
            return Component.text(input == null ? "" : input);
        }
    }

    public static void send(CommandSender sender, PluginSettings settings, String message) {
        sender.sendMessage(mm(settings.message("prefix", "<gold>MaddKraft » </gold>")).append(mm(message)));
    }

    public static void raw(CommandSender sender, String message) {
        sender.sendMessage(mm(message));
    }

    public static String number(double value) {
        synchronized (NUMBER) {
            return NUMBER.format(value);
        }
    }

    public static String integer(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public static String duration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1L, minutes) + "m";
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }
}
