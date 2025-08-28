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
    private final ArrayList<Command> commands;
    private Command defaultCommand;
    private final String globalCommand;
    private final String commandPermission;
    private final Config messages;
    private final boolean sendHelp;
    private final Consumer<CommandSender> onEmptyArgsHandler;

    private String noPermissionMessagePath;
    private String incorrectUsageMessagePath;
    private String helpMessagesPath;

    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission) {
        this(plugin, messages, globalCommand, commandPermission, true, null);
    }

    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission, Command defaultCommand) {
        this(plugin, messages, globalCommand, commandPermission, false, null);
        this.defaultCommand = defaultCommand;
    }

    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission, boolean sendHelp) {
        this(plugin, messages, globalCommand, commandPermission, sendHelp, null);
    }

    public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission, Consumer<CommandSender> onEmptyArgsHandler) {
        this(plugin, messages, globalCommand, commandPermission, false, onEmptyArgsHandler);
    }

    private CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission, boolean sendHelp, Consumer<CommandSender> onEmptyArgsHandler) {
        this.plugin = plugin;
        this.commands = new ArrayList<>();
        this.messages = messages;
        this.globalCommand = globalCommand;
        this.commandPermission = commandPermission;
        this.sendHelp = sendHelp;
        this.onEmptyArgsHandler = onEmptyArgsHandler;

        this.noPermissionMessagePath = "messages.commands.no_permission";
        this.incorrectUsageMessagePath = "messages.commands.incorrect_usage";
        this.helpMessagesPath = "messages.commands.help";

        this.defaultCommand = null;

        plugin.getCommand(globalCommand).setExecutor(this);
    }

    public void addCommand(Command c) {
        commands.add(c);
    }

    public ArrayList<Command> getCommands() {
        return commands;
    }

    public Command getCommand(String arg) {
        for (Command c : getCommands()) {
            if (c.getAliases().contains(arg)) return c;
        }
        return null;
    }

    public void help(CommandSender sender) {
        int i = 0;
        StringBuilder helpMessage = new StringBuilder();
        List<String> helpMessages = messages.getStringList(helpMessagesPath);

        for (String m : helpMessages) {
            int startPermissionIndex = m.indexOf("{!");
            String permission = null;
            if (startPermissionIndex >= 0) {
                permission = m.substring(startPermissionIndex + 2, m.indexOf("}"));
            }
            if (permission == null || sender.hasPermission(permission)) {
                helpMessage.append(m.replace("{!" + permission + "}", ""));
                if (helpMessages.size() - 1 != i) helpMessage.append(" \n");
            }
            i++;
        }
        sender.sendMessage(helpMessage.toString());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.Command cmd, @NotNull String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase(globalCommand)) return false;
        String noPermission = messages.getString(noPermissionMessagePath);
        if (args.length == 0) {
            if (onEmptyArgsHandler != null) {
                onEmptyArgsHandler.accept(sender);
                return true;
            }

            if (defaultCommand != null && sender.hasPermission(commandPermission)) {
                defaultCommand.execute(plugin, sender, args);
                return true;
            }

            if (sendHelp && sender.hasPermission(commandPermission)) {
                help(sender);
            } else if (sendHelp) {
                if (noPermission != null && !noPermission.isEmpty()) sender.sendMessage(noPermission);
            } else {
                Command command = getCommand(globalCommand);
                command.execute(plugin, sender, args);

            }
            return false;
        }


        Command command = getCommand(args[0]);

        if (command != null) {
            if (!(sender instanceof Player) && !command.canBeExecutedByConsole()) {
                sender.sendMessage("§cCan be executed only by players!");
                return false;
            }
            if (!command.getPermission().isEmpty() && !sender.hasPermission(command.getPermission())) {
                if (noPermission != null && !noPermission.isEmpty()) sender.sendMessage(noPermission);
                return false;
            }
            if (args.length < command.getMinArgs() || args.length > command.getMaxArgs()) {

                String incorrectUsageMessage = messages.getString(incorrectUsageMessagePath, globalCommand + " " + command.getUsage());
                if (incorrectUsageMessage != null && !incorrectUsageMessage.isEmpty())
                    sender.sendMessage(incorrectUsageMessage);
                return false;
            }
            command.execute(plugin, sender, args);
        } else if (defaultCommand != null) {
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
                String incorrectUsageMessage = messages.getString(incorrectUsageMessagePath, globalCommand + " " + defaultCommand.getUsage());
                if (incorrectUsageMessage != null && !incorrectUsageMessage.isEmpty()) sender.sendMessage(incorrectUsageMessage);
                return true;
            }
            defaultCommand.execute(plugin, sender, args);
            return true;
        } else if (noPermission != null && !noPermission.isEmpty())
            sender.sendMessage(noPermission);

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command cmd, @NotNull String alias, String[] args) {
        if (args.length > 0) {
            Command command = getCommand(args[0]);
            if (command != null) {
                return command.getPermission() != null && !sender.hasPermission(command.getPermission()) ? new ArrayList<>() : command.tabComplete(plugin, sender, args);
            }
        }

        List<String> list = new ArrayList<>();

        for (Command subCommand : getCommands()) {
            if (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission())) {
                for (String aliases : subCommand.getAliases()) {
                    if (aliases.contains(args[0].toLowerCase())) {
                        list.add(aliases);
                    }
                }
            }
        }

        return list;
    }

    public void setNoPermissionMessagePath(String noPermissionMessagePath) {
        this.noPermissionMessagePath = noPermissionMessagePath;
    }

    public void setIncorrectUsageMessagePath(String incorrectUsageMessagePath) {
        this.incorrectUsageMessagePath = incorrectUsageMessagePath;
    }

    public void setHelpMessagesPath(String helpMessagesPath) {
        this.helpMessagesPath = helpMessagesPath;
    }
}
