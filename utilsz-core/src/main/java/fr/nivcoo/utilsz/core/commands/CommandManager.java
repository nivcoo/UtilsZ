package fr.nivcoo.utilsz.core.commands;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CommandManager implements CommandDispatcher {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ArrayList<Command> commands = new ArrayList<>();
    private final CommandsConfigProvider provider;

    private final String globalCommand;
    private final String commandPermission;

    private final boolean sendHelp;
    private final Consumer<Sender> onEmptyArgsHandler;
    private Command defaultCommand;

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission
    ) {
        this(registrar, provider, globalCommand, commandPermission, true, null, null);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission,
            boolean sendHelp
    ) {
        this(registrar, provider, globalCommand, commandPermission, sendHelp, null, null);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission,
            Command defaultCommand
    ) {
        this(registrar, provider, globalCommand, commandPermission, false, null, defaultCommand);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission,
            Consumer<Sender> onEmptyArgsHandler
    ) {
        this(registrar, provider, globalCommand, commandPermission, false, onEmptyArgsHandler, null);
    }

    private CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission,
            boolean sendHelp,
            Consumer<Sender> onEmptyArgsHandler,
            Command defaultCommand
    ) {
        this.provider = provider;
        this.globalCommand = globalCommand;
        this.commandPermission = commandPermission;
        this.sendHelp = sendHelp;
        this.onEmptyArgsHandler = onEmptyArgsHandler;
        this.defaultCommand = defaultCommand;

        registrar.registerRoot(globalCommand, this);
    }

    public void addCommand(Command c) { commands.add(c); }
    public ArrayList<Command> getCommands() { return commands; }
    public void setDefaultCommand(Command c) { this.defaultCommand = c; }

    public Command getCommand(String arg) {
        for (Command c : commands) if (c.getAliases().contains(arg)) return c;
        return null;
    }

    private static Component fmtComp(Component template, String... args) {
        if (template == null) return Component.empty();
        String plain = PLAIN.serialize(template);
        for (int i = 0; i < args.length; i++) {
            plain = plain.replace("{" + i + "}", args[i] == null ? "" : args[i]);
        }
        return ConfigManager.parseDynamic(plain);
    }

    public void help(Sender sender) {
        List<Component> lines = provider.help();
        if (lines == null || lines.isEmpty()) return;

        List<Component> toSend = new ArrayList<>(lines.size());
        for (Component c : lines) {
            if (c == null) continue;
            String plain = PLAIN.serialize(c);
            int idx = plain.indexOf("{!");
            String perm = null;
            if (idx >= 0) {
                int end = plain.indexOf("}", idx + 2);
                if (end > idx) perm = plain.substring(idx + 2, end);
            }
            if (perm == null || sender.hasPermission(perm)) {
                String shown = (perm == null) ? plain : plain.replace("{!" + perm + "}", "");
                toSend.add(ConfigManager.parseDynamic(shown));
            }
        }

        for (Component line : toSend) sender.sendMessage(line);
    }

    @Override
    public boolean dispatch(Sender sender, String label, String[] args) {
        Component noPermission = provider.noPermission();

        if (args.length == 0) {
            if (onEmptyArgsHandler != null) { onEmptyArgsHandler.accept(sender); return true; }

            if (defaultCommand != null && sender.hasPermission(commandPermission)) {
                defaultCommand.execute(new CommandContext(sender, label, args));
                return true;
            }

            if (sendHelp && sender.hasPermission(commandPermission)) {
                help(sender);
                return true;
            }

            if (sendHelp) {
                if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
                return true;
            }

            Command c = getCommand(globalCommand);
            if (c != null) c.execute(new CommandContext(sender, label, args));
            return true;
        }

        Command sub = getCommand(args[0]);
        if (sub != null) {
            if (sender.isConsole() && !sub.canBeExecutedByConsole()) {
                sender.sendMessage(ConfigManager.parseDynamic("§cCan be executed only by players!"));
                return true;
            }

            String perm = sub.getPermission();
            if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
                if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
                return true;
            }

            if (args.length < sub.getMinArgs() || args.length > sub.getMaxArgs()) {
                Component msgTpl = provider.incorrectUsage();
                Component msg = fmtComp(msgTpl, globalCommand + " " + sub.getUsage());
                if (isNotEmpty(msg)) sender.sendMessage(msg);
                return true;
            }

            sub.execute(new CommandContext(sender, label, args));
            return true;
        }

        if (defaultCommand != null) {
            if (sender.isConsole() && !defaultCommand.canBeExecutedByConsole()) {
                sender.sendMessage(ConfigManager.parseDynamic("§cCan be executed only by players!"));
                return true;
            }

            String perm = defaultCommand.getPermission();
            if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
                if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
                return true;
            }

            if (args.length < defaultCommand.getMinArgs() || args.length > defaultCommand.getMaxArgs()) {
                Component msgTpl = provider.incorrectUsage();
                Component msg = fmtComp(msgTpl, globalCommand + " " + defaultCommand.getUsage());
                if (isNotEmpty(msg)) sender.sendMessage(msg);
                return true;
            }

            defaultCommand.execute(new CommandContext(sender, label, args));
            return true;
        }

        if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
        return true;
    }

    @Override
    public List<String> tabComplete(Sender sender, String label, String[] args) {
        if (args.length > 0) {
            Command c = getCommand(args[0]);
            if (c != null) {
                String perm = c.getPermission();
                if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) return List.of();
                return c.tabComplete(new CommandContext(sender, label, args));
            }
        }

        List<String> list = new ArrayList<>();
        String prefix = args.length > 0 ? args[0].toLowerCase() : "";

        for (Command sc : commands) {
            String perm = sc.getPermission();
            if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                for (String al : sc.getAliases()) {
                    if (al.toLowerCase().contains(prefix)) list.add(al);
                }
            }
        }
        return list;
    }

    private static boolean isNotEmpty(Component c) {
        if (c == null) return false;
        String plain = PLAIN.serialize(c);
        return !plain.isEmpty();
    }
}
