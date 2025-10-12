package fr.nivcoo.utilsz.commands;

import fr.nivcoo.utilsz.config.Config;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CommandManager implements TabExecutor {

    private final JavaPlugin plugin;
    private final ArrayList<Command> commands = new ArrayList<>();
    private Command defaultCommand;
    private final String globalCommand;
    private final String commandPermission;

    private final CommandsConfigProvider provider;
    private final boolean sendHelp;
    private final Consumer<CommandSender> onEmptyArgsHandler;

    public CommandManager(JavaPlugin plugin,
                          CommandsConfigProvider provider,
                          String globalCommand,
                          String commandPermission) {
        this(plugin, provider, globalCommand, commandPermission, true, null);
    }

    public CommandManager(JavaPlugin plugin,
                          CommandsConfigProvider provider,
                          String globalCommand,
                          String commandPermission,
                          Command defaultCommand) {
        this(plugin, provider, globalCommand, commandPermission, false, null);
        this.defaultCommand = defaultCommand;
    }

    public CommandManager(JavaPlugin plugin,
                          CommandsConfigProvider provider,
                          String globalCommand,
                          String commandPermission,
                          boolean sendHelp) {
        this(plugin, provider, globalCommand, commandPermission, sendHelp, null);
    }

    public CommandManager(JavaPlugin plugin,
                          CommandsConfigProvider provider,
                          String globalCommand,
                          String commandPermission,
                          Consumer<CommandSender> onEmptyArgsHandler) {
        this(plugin, provider, globalCommand, commandPermission, false, onEmptyArgsHandler);
    }

    private CommandManager(JavaPlugin plugin,
                           CommandsConfigProvider provider,
                           String globalCommand,
                           String commandPermission,
                           boolean sendHelp,
                           Consumer<CommandSender> onEmptyArgsHandler) {
        this.plugin = plugin;
        this.provider = provider;
        this.globalCommand = globalCommand;
        this.commandPermission = commandPermission;
        this.sendHelp = sendHelp;
        this.onEmptyArgsHandler = onEmptyArgsHandler;
        this.defaultCommand = null;
        plugin.getCommand(globalCommand).setExecutor(this);
    }

    @Deprecated
    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission) {
        this(plugin,
                new PathCommandsConfigProvider(messages,
                        "messages.commands.no_permission",
                        "messages.commands.incorrect_usage",
                        "messages.commands.help"),
                globalCommand, commandPermission, true, null);
    }

    @Deprecated
    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission, Command defaultCommand) {
        this(plugin,
                new PathCommandsConfigProvider(messages,
                        "messages.commands.no_permission",
                        "messages.commands.incorrect_usage",
                        "messages.commands.help"),
                globalCommand, commandPermission, false, null);
        this.defaultCommand = defaultCommand;
    }

    @Deprecated
    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission, boolean sendHelp) {
        this(plugin,
                new PathCommandsConfigProvider(messages,
                        "messages.commands.no_permission",
                        "messages.commands.incorrect_usage",
                        "messages.commands.help"),
                globalCommand, commandPermission, sendHelp, null);
    }

    @Deprecated
    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission, Consumer<CommandSender> onEmptyArgsHandler) {
        this(plugin,
                new PathCommandsConfigProvider(messages,
                        "messages.commands.no_permission",
                        "messages.commands.incorrect_usage",
                        "messages.commands.help"),
                globalCommand, commandPermission, false, onEmptyArgsHandler);
    }

    public void addCommand(Command c) { commands.add(c); }
    public ArrayList<Command> getCommands() { return commands; }

    public Command getCommand(String arg) {
        for (Command c : commands) if (c.getAliases().contains(arg)) return c;
        return null;
    }

    private static String fmt(String template, String... args) {
        if (template == null) return null;
        String out = template;
        for (int i = 0; i < args.length; i++) out = out.replace("{"+i+"}", args[i] == null ? "" : args[i]);
        return out;
    }

    public void help(CommandSender sender) {
        List<String> lines = provider.help();
        if (lines == null || lines.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String m : lines) {
            if (m == null) { i++; continue; }
            int idx = m.indexOf("{!");
            String perm = null;
            if (idx >= 0) {
                int end = m.indexOf("}", idx + 2);
                if (end > idx) perm = m.substring(idx + 2, end);
            }
            if (perm == null || sender.hasPermission(perm)) {
                sb.append(perm == null ? m : m.replace("{!" + perm + "}", ""));
                if (i < lines.size() - 1) sb.append("\n");
            }
            i++;
        }
        sender.sendMessage(sb.toString());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.Command cmd, @NotNull String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase(globalCommand)) return false;

        String noPermission = provider.noPermission();

        if (args.length == 0) {
            if (onEmptyArgsHandler != null) { onEmptyArgsHandler.accept(sender); return true; }

            if (defaultCommand != null && sender.hasPermission(commandPermission)) {
                defaultCommand.execute(plugin, sender, args);
                return true;
            }

            if (sendHelp && sender.hasPermission(commandPermission)) {
                help(sender);
            } else if (sendHelp) {
                if (noPermission != null && !noPermission.isEmpty()) sender.sendMessage(noPermission);
            } else {
                Command c = getCommand(globalCommand);
                if (c != null) c.execute(plugin, sender, args);
            }
            return false;
        }

        Command sub = getCommand(args[0]);
        if (sub != null) {
            if (!(sender instanceof Player) && !sub.canBeExecutedByConsole()) {
                sender.sendMessage("§cCan be executed only by players!");
                return false;
            }
            if (!sub.getPermission().isEmpty() && !sender.hasPermission(sub.getPermission())) {
                if (noPermission != null && !noPermission.isEmpty()) sender.sendMessage(noPermission);
                return false;
            }
            if (args.length < sub.getMinArgs() || args.length > sub.getMaxArgs()) {
                String msg = provider.incorrectUsage();
                msg = fmt(msg, globalCommand + " " + sub.getUsage());
                if (msg != null && !msg.isEmpty()) sender.sendMessage(msg);
                return false;
            }
            sub.execute(plugin, sender, args);
            return true;
        }

        if (defaultCommand != null) {
            if (!(sender instanceof Player) && !defaultCommand.canBeExecutedByConsole()) {
                sender.sendMessage("§cCan be executed only by players!");
                return true;
            }
            if (defaultCommand.getPermission() != null && !defaultCommand.getPermission().isEmpty()
                    && !sender.hasPermission(defaultCommand.getPermission())) {
                if (noPermission != null && !noPermission.isEmpty()) sender.sendMessage(noPermission);
                return true;
            }
            if (args.length < defaultCommand.getMinArgs() || args.length > defaultCommand.getMaxArgs()) {
                String msg = provider.incorrectUsage();
                msg = fmt(msg, globalCommand + " " + defaultCommand.getUsage());
                if (msg != null && !msg.isEmpty()) sender.sendMessage(msg);
                return true;
            }
            defaultCommand.execute(plugin, sender, args);
            return true;
        }

        if (noPermission != null && !noPermission.isEmpty()) sender.sendMessage(noPermission);
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command cmd, @NotNull String alias, String[] args) {
        if (args.length > 0) {
            Command c = getCommand(args[0]);
            if (c != null) return (c.getPermission() != null && !sender.hasPermission(c.getPermission())) ? List.of() : c.tabComplete(plugin, sender, args);
        }
        List<String> list = new ArrayList<>();
        String prefix = args.length > 0 ? args[0].toLowerCase() : "";
        for (Command sc : commands) {
            if (sc.getPermission() == null || sender.hasPermission(sc.getPermission())) {
                for (String al : sc.getAliases()) if (al.toLowerCase().contains(prefix)) list.add(al);
            }
        }
        return list;
    }
}
