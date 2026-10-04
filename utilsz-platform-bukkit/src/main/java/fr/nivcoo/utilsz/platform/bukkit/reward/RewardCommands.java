package fr.nivcoo.utilsz.platform.bukkit.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public final class RewardCommands {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,32}");
    private static final Pattern VARIABLE = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private RewardCommands() {
    }

    public static String normalize(String command) {
        if (command == null || command.isBlank() || command.length() > 1024
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid reward command.");
        String value = command.strip();
        while (value.startsWith("/")) value = value.substring(1).stripLeading();
        if (value.isBlank()) throw new IllegalArgumentException("Reward command is empty.");
        return value;
    }

    public static String render(String template, RewardExecutionContext context) {
        String source = normalize(template);
        Player player = context.player();
        if (source.contains("{player}") && (player == null || !PLAYER_NAME.matcher(player.getName()).matches()))
            throw new IllegalArgumentException("Reward command requires a valid player name.");
        Map<String, String> variables = context.variables();
        String command = VARIABLE.matcher(source).replaceAll(match -> {
            String key = match.group(1);
            String value = switch (key) {
                case "player" -> player == null ? null : player.getName();
                case "uuid", "player_uuid" -> context.playerId().toString();
                default -> variables.get(key);
            };
            return java.util.regex.Matcher.quoteReplacement(value == null ? match.group() : value);
        });
        return normalize(command);
    }

    public static boolean registered(String command) {
        return Bukkit.getCommandMap().getCommand(root(command)) != null;
    }

    public static String root(String command) {
        String normalized = normalize(command);
        int separator = normalized.indexOf(' ');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }
}
