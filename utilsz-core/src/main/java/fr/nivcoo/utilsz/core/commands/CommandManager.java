package fr.nivcoo.utilsz.core.commands;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class CommandManager implements CommandDispatcher {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ArrayList<Command> commands = new ArrayList<>();
    private final ArrayList<NestedCommand> nestedCommands = new ArrayList<>();
    private final ArrayList<RegisteredSection> sections = new ArrayList<>();
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
        this(registrar, provider, globalCommand, List.of(), commandPermission, true, null, null);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            List<String> rootAliases,
            String commandPermission
    ) {
        this(registrar, provider, globalCommand, rootAliases, commandPermission, true, null, null);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission,
            boolean sendHelp
    ) {
        this(registrar, provider, globalCommand, List.of(), commandPermission, sendHelp, null, null);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            List<String> rootAliases,
            String commandPermission,
            boolean sendHelp
    ) {
        this(registrar, provider, globalCommand, rootAliases, commandPermission, sendHelp, null, null);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission,
            Command defaultCommand
    ) {
        this(registrar, provider, globalCommand, List.of(), commandPermission, false, null, defaultCommand);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            List<String> rootAliases,
            String commandPermission,
            Command defaultCommand
    ) {
        this(registrar, provider, globalCommand, rootAliases, commandPermission, false, null, defaultCommand);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            String commandPermission,
            Consumer<Sender> onEmptyArgsHandler
    ) {
        this(registrar, provider, globalCommand, List.of(), commandPermission, false, onEmptyArgsHandler, null);
    }

    public CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            List<String> rootAliases,
            String commandPermission,
            Consumer<Sender> onEmptyArgsHandler
    ) {
        this(registrar, provider, globalCommand, rootAliases, commandPermission, false, onEmptyArgsHandler, null);
    }

    private CommandManager(
            CommandRegistrar registrar,
            CommandsConfigProvider provider,
            String globalCommand,
            List<String> rootAliases,
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

        registrar.registerRoot(globalCommand, List.copyOf(rootAliases), this);
    }

    public void addCommand(Command command) {
        requireAliases(command);
        requireUniqueCommand(command);
        requireAvailableAliases(List.of(), command.getAliases());
        commands.add(command);
    }

    public CommandSection addSection(String name, String... aliases) {
        return addSection(List.of(), name, aliases);
    }

    CommandSection addSection(List<String> parentPath, String name, String... aliases) {
        List<String> sectionAliases = requireSectionAliases(name, aliases);
        String segment = sectionAliases.getFirst();
        List<String> path = new ArrayList<>(parentPath.size() + 1);
        path.addAll(parentPath);
        path.add(segment);
        List<String> immutablePath = List.copyOf(path);
        RegisteredSection existing = findSection(immutablePath);
        if (existing == null) {
            requireAvailableSectionAliases(parentPath, sectionAliases);
            sections.add(new RegisteredSection(resolveSections(parentPath), immutablePath, sectionAliases));
        } else if (!sameAliases(existing.aliases(), sectionAliases)) {
            throw new IllegalArgumentException("Command section is already registered with different aliases");
        }
        return new CommandSection(this, immutablePath);
    }

    void addCommand(List<String> parentPath, Command command) {
        requireAliases(command);
        requireUniqueCommand(command);
        requireAvailableAliases(parentPath, command.getAliases());
        nestedCommands.add(new NestedCommand(resolveSections(parentPath), command));
    }

    void setSectionPermission(List<String> path, String permission) {
        requireSection(path).permission = permission == null ? "" : permission;
    }

    void setSectionConsoleAllowed(List<String> path, boolean allowed) {
        requireSection(path).consoleAllowed = allowed;
    }

    public ArrayList<Command> getCommands() { return commands; }

    public void setDefaultCommand(Command command) {
        if (command != null && findRegistration(command) != null) {
            throw new IllegalArgumentException("A command instance can only be registered once per manager");
        }
        this.defaultCommand = command;
    }

    public Command getCommand(String arg) {
        for (Command c : commands) {
            if (matchesAlias(c, arg)) return c;
        }
        return null;
    }

    public String getUsage(Command command, CommandContext context) {
        if (command == null) throw new NullPointerException("command");
        if (context == null) throw new NullPointerException("context");
        if (command == defaultCommand) return rootUsage(command.getUsage(context));
        CommandMatch match = findRegistration(command);
        if (match == null) throw new IllegalArgumentException("Command is not registered in this manager");
        return usage(match, context);
    }

    public void sendUsage(Command command, CommandContext context) {
        Component message = fmtComp(provider.incorrectUsage(), getUsage(command, context));
        if (isNotEmpty(message)) context.sender().sendMessage(message);
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
                if (sender.isConsole() && !defaultCommand.canBeExecutedByConsole()) {
                    sendPlayerOnly(sender);
                    return true;
                }
                String permission = defaultCommand.getPermission();
                if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
                    if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
                    return true;
                }
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

            if (defaultCommand != null && isNotEmpty(noPermission)) {
                sender.sendMessage(noPermission);
                return true;
            }

            Command c = getCommand(globalCommand);
            if (c != null) c.execute(new CommandContext(sender, label, args));
            return true;
        }

        CommandMatch match = findCommand(args);
        if (match != null) {
            Command sub = match.command();
            String[] localArgs = match.localArgs(args);
            CommandContext context = new CommandContext(sender, label, localArgs);
            if (localArgs.length < sub.getMinArgs() || localArgs.length > sub.getMaxArgs()) {
                RegisteredSection section = findDeepestSection(args);
                if (section != null && section.path.size() >= match.routeLength()) {
                    sendSectionUsage(sender, section);
                    return true;
                }
            }

            if (sender.isConsole() && !sub.canBeExecutedByConsole()) {
                sendPlayerOnly(sender);
                return true;
            }

            String perm = sub.getPermission();
            if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
                if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
                return true;
            }

            if (localArgs.length < sub.getMinArgs() || localArgs.length > sub.getMaxArgs()) {
                Component msgTpl = provider.incorrectUsage();
                Component msg = fmtComp(msgTpl, usage(match, context));
                if (isNotEmpty(msg)) sender.sendMessage(msg);
                return true;
            }

            sub.execute(context);
            return true;
        }

        RegisteredSection section = findDeepestSection(args);
        if (section != null) {
            sendSectionUsage(sender, section);
            return true;
        }

        if (defaultCommand != null) {
            if (sender.isConsole() && !defaultCommand.canBeExecutedByConsole()) {
                sendPlayerOnly(sender);
                return true;
            }

            String perm = defaultCommand.getPermission();
            if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
                if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
                return true;
            }

            if (args.length < defaultCommand.getMinArgs() || args.length > defaultCommand.getMaxArgs()) {
                Component msgTpl = provider.incorrectUsage();
                CommandContext context = new CommandContext(sender, label, args);
                Component msg = fmtComp(msgTpl, rootUsage(defaultCommand.getUsage(context)));
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
        LinkedHashSet<String> suggestions = new LinkedHashSet<>(routeSuggestions(sender, args));
        CommandMatch match = findCommand(args);
        if (match != null && args.length > match.routeLength()) {
            Command command = match.command();
            if (canUse(sender, command)) {
                suggestions.addAll(command.tabComplete(
                        new CommandContext(sender, label, match.localArgs(args))));
            }
        }

        if (args.length <= 1) {
            ArrayList<String> defaults = new ArrayList<>();
            appendDefaultSuggestions(defaults, sender, label, args);
            suggestions.addAll(defaults);
        }
        return new ArrayList<>(suggestions);
    }

    private void appendDefaultSuggestions(List<String> suggestions, Sender sender, String label, String[] args) {
        if (defaultCommand != null) {
            if (sender.isConsole() && !defaultCommand.canBeExecutedByConsole()) return;
            String perm = defaultCommand.getPermission();
            if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                suggestions.addAll(defaultCommand.tabComplete(new CommandContext(sender, label, args)));
            }
        }
    }

    private static boolean isNotEmpty(Component c) {
        if (c == null) return false;
        String plain = PLAIN.serialize(c);
        return !plain.isEmpty();
    }

    private void sendPlayerOnly(Sender sender) {
        Component playerOnly = provider.playerOnly();
        if (isNotEmpty(playerOnly)) sender.sendMessage(playerOnly);
    }

    private String usage(CommandMatch match, CommandContext ctx) {
        Command command = match.command();
        String usage = command == null ? "" : command.getUsage(ctx);
        String route = match.canonicalRoute();
        if (usage == null || usage.isBlank()) return globalCommand + " " + route;
        String trimmed = usage.trim();
        if (trimmed.equalsIgnoreCase(globalCommand)) return globalCommand;
        String prefix = globalCommand + " ";
        if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) return trimmed;
        if (startsWithRoute(trimmed, route)) return prefix + trimmed;

        String alias = match.primaryAlias();
        if (startsWithRoute(trimmed, alias)) {
            if (match.parentPath().isEmpty()) return prefix + trimmed;
            return prefix + String.join(" ", match.parentPath()) + " " + trimmed;
        }
        return prefix + route + " " + trimmed;
    }

    private String rootUsage(String usage) {
        if (usage == null || usage.isBlank()) return globalCommand;
        String trimmed = usage.trim();
        if (trimmed.equalsIgnoreCase(globalCommand)) return globalCommand;
        String prefix = globalCommand + " ";
        if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) return trimmed;
        return prefix + trimmed;
    }

    private List<String> routeSuggestions(Sender sender, String[] args) {
        int cursor = args.length == 0 ? 0 : args.length - 1;
        String prefix = args.length == 0 ? "" : args[cursor].toLowerCase(Locale.ROOT);
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();

        for (Command command : commands) {
            collectSuggestions(suggestions, sender, new CommandMatch(List.of(), command), args, cursor, prefix);
        }
        for (NestedCommand nested : nestedCommands) {
            collectSuggestions(suggestions, sender, new CommandMatch(nested.parentSections(), nested.command()),
                    args, cursor, prefix);
        }
        return new ArrayList<>(suggestions);
    }

    private void collectSuggestions(
            LinkedHashSet<String> suggestions,
            Sender sender,
            CommandMatch match,
            String[] args,
            int cursor,
            String prefix
    ) {
        if (!canUse(sender, match.command()) || cursor >= match.routeLength()) return;
        for (int i = 0; i < cursor; i++) {
            if (!match.matchesSegment(i, args[i])) return;
        }
        for (String candidate : match.aliasesAt(cursor)) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) suggestions.add(candidate);
        }
    }

    private CommandMatch findCommand(String[] args) {
        if (args.length == 0) return null;
        CommandMatch best = null;
        for (Command command : commands) {
            CommandMatch candidate = new CommandMatch(List.of(), command);
            if (best == null && candidate.matches(args)) best = candidate;
        }
        for (NestedCommand nested : nestedCommands) {
            CommandMatch candidate = new CommandMatch(nested.parentSections(), nested.command());
            if (candidate.matches(args) && (best == null || candidate.routeLength() > best.routeLength())) {
                best = candidate;
            }
        }
        return best;
    }

    private CommandMatch findRegistration(Command command) {
        for (Command registered : commands) {
            if (registered == command) return new CommandMatch(List.of(), registered);
        }
        for (NestedCommand nested : nestedCommands) {
            if (nested.command() == command) return new CommandMatch(nested.parentSections(), nested.command());
        }
        return null;
    }

    private RegisteredSection findSection(List<String> path) {
        for (RegisteredSection section : sections) {
            if (samePath(section.path, path)) return section;
        }
        return null;
    }

    private RegisteredSection requireSection(List<String> path) {
        RegisteredSection section = findSection(path);
        if (section == null) throw new IllegalArgumentException("Command section is not registered");
        return section;
    }

    private RegisteredSection findDeepestSection(String[] args) {
        RegisteredSection best = null;
        for (RegisteredSection section : sections) {
            if (!section.matches(args)) continue;
            if (best == null || section.path.size() > best.path.size()) best = section;
        }
        return best;
    }

    private void sendSectionUsage(Sender sender, RegisteredSection section) {
        if (sender.isConsole() && !section.consoleAllowed) {
            sendPlayerOnly(sender);
            return;
        }
        if (!section.permission.isEmpty() && !sender.hasPermission(section.permission)) {
            Component noPermission = provider.noPermission();
            if (isNotEmpty(noPermission)) sender.sendMessage(noPermission);
            return;
        }
        Component message = fmtComp(provider.incorrectUsage(), sectionUsage(section, sender));
        if (isNotEmpty(message)) sender.sendMessage(message);
    }

    private String sectionUsage(RegisteredSection section, Sender sender) {
        LinkedHashSet<String> children = new LinkedHashSet<>();
        for (Command command : commands) {
            collectSectionChild(children, section.path, new CommandMatch(List.of(), command), sender);
        }
        for (NestedCommand nested : nestedCommands) {
            collectSectionChild(children, section.path,
                    new CommandMatch(nested.parentSections(), nested.command()), sender);
        }
        String base = globalCommand + " " + String.join(" ", section.path);
        if (children.isEmpty()) return base;
        return base + " <" + String.join("|", children) + ">";
    }

    private static void collectSectionChild(
            LinkedHashSet<String> children,
            List<String> sectionPath,
            CommandMatch match,
            Sender sender
    ) {
        List<String> route = match.canonicalPath();
        if (route.size() <= sectionPath.size() || !startsWithPath(route, sectionPath)) return;
        if (canUse(sender, match.command())) children.add(route.get(sectionPath.size()));
    }

    private static boolean canUse(Sender sender, Command command) {
        if (sender.isConsole() && !command.canBeExecutedByConsole()) return false;
        String permission = command.getPermission();
        return permission == null || permission.isEmpty() || sender.hasPermission(permission);
    }

    private static boolean matchesAlias(Command command, String value) {
        if (value == null) return false;
        for (String alias : command.getAliases()) {
            if (alias.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static boolean samePath(List<String> left, List<String> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equalsIgnoreCase(right.get(i))) return false;
        }
        return true;
    }

    private static boolean startsWithPath(List<String> route, List<String> prefix) {
        if (route.size() < prefix.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!route.get(i).equalsIgnoreCase(prefix.get(i))) return false;
        }
        return true;
    }

    private static void requireAliases(Command command) {
        if (command == null) throw new NullPointerException("command");
        List<String> aliases = command.getAliases();
        if (aliases == null || aliases.isEmpty() || aliases.stream().anyMatch(alias -> alias == null || alias.isBlank())) {
            throw new IllegalArgumentException("A command must declare at least one non-empty alias");
        }
        if (aliases.stream().anyMatch(alias -> alias.chars().anyMatch(Character::isWhitespace))) {
            throw new IllegalArgumentException("Command aliases cannot contain whitespace");
        }
        requireDistinctAliases(aliases, "Command aliases must be unique");
    }

    private void requireUniqueCommand(Command command) {
        if (command == defaultCommand
                || commands.stream().anyMatch(registered -> registered == command)
                || nestedCommands.stream().anyMatch(registered -> registered.command() == command)) {
            throw new IllegalArgumentException("A command instance can only be registered once per manager");
        }
    }

    private void requireAvailableAliases(List<String> parentPath, List<String> aliases) {
        if (parentPath.isEmpty()) {
            for (Command command : commands) {
                if (aliasesOverlap(command.getAliases(), aliases)) {
                    throw new IllegalArgumentException("A command alias is already registered at this level");
                }
            }
        } else {
            for (NestedCommand nested : nestedCommands) {
                if (samePath(canonicalPath(nested.parentSections()), parentPath)
                        && aliasesOverlap(nested.command().getAliases(), aliases)) {
                    throw new IllegalArgumentException("A command alias is already registered at this level");
                }
            }
        }

        for (RegisteredSection section : sections) {
            List<String> sectionParent = section.path.subList(0, section.path.size() - 1);
            if (!samePath(sectionParent, parentPath) || !aliasesOverlap(section.aliases(), aliases)) continue;
            if (!sameAliases(section.aliases(), aliases)) {
                throw new IllegalArgumentException(
                        "An executable command and its section must declare the same aliases");
            }
        }
    }

    private static List<String> requireSectionAliases(String name, String... aliases) {
        ArrayList<String> values = new ArrayList<>();
        values.add(requireRouteSegment(name, "Command section name"));
        if (aliases != null) {
            for (String alias : aliases) {
                values.add(requireRouteSegment(alias, "Command section alias"));
            }
        }
        requireDistinctAliases(values, "Command section aliases must be unique");
        return List.copyOf(values);
    }

    private void requireAvailableSectionAliases(List<String> parentPath, List<String> aliases) {
        for (RegisteredSection section : sections) {
            List<String> sectionParent = section.path.subList(0, section.path.size() - 1);
            if (samePath(sectionParent, parentPath) && aliasesOverlap(section.aliases(), aliases)) {
                throw new IllegalArgumentException("A command section alias is already registered at this level");
            }
        }

        if (parentPath.isEmpty()) {
            for (Command command : commands) {
                validateExecutableSectionAliases(command.getAliases(), aliases);
            }
            return;
        }

        for (NestedCommand nested : nestedCommands) {
            if (samePath(canonicalPath(nested.parentSections()), parentPath)) {
                validateExecutableSectionAliases(nested.command().getAliases(), aliases);
            }
        }
    }

    private static void validateExecutableSectionAliases(
            List<String> commandAliases,
            List<String> sectionAliases
    ) {
        if (!aliasesOverlap(commandAliases, sectionAliases)) return;
        if (!sameAliases(commandAliases, sectionAliases)) {
            throw new IllegalArgumentException(
                    "An executable command and its section must declare the same aliases");
        }
    }

    private List<RegisteredSection> resolveSections(List<String> path) {
        if (path.isEmpty()) return List.of();
        ArrayList<RegisteredSection> resolved = new ArrayList<>(path.size());
        for (int depth = 1; depth <= path.size(); depth++) {
            RegisteredSection section = findSection(path.subList(0, depth));
            if (section == null) throw new IllegalArgumentException("Command section is not registered");
            resolved.add(section);
        }
        return List.copyOf(resolved);
    }

    private static String requireRouteSegment(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " cannot be empty");
        String trimmed = value.trim();
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(label + " cannot contain whitespace");
        }
        return trimmed;
    }

    private static void requireDistinctAliases(List<String> aliases, String message) {
        for (int left = 0; left < aliases.size(); left++) {
            for (int right = left + 1; right < aliases.size(); right++) {
                if (aliases.get(left).equalsIgnoreCase(aliases.get(right))) {
                    throw new IllegalArgumentException(message);
                }
            }
        }
    }

    private static boolean aliasesOverlap(List<String> left, List<String> right) {
        for (String leftAlias : left) {
            for (String rightAlias : right) {
                if (leftAlias.equalsIgnoreCase(rightAlias)) return true;
            }
        }
        return false;
    }

    private static boolean sameAliases(List<String> left, List<String> right) {
        return samePath(left, right);
    }

    private static List<String> canonicalPath(List<RegisteredSection> sections) {
        return sections.stream().map(RegisteredSection::primaryAlias).toList();
    }

    private static boolean startsWithRoute(String value, String route) {
        if (value.equalsIgnoreCase(route)) return true;
        int length = route.length();
        return value.length() > length
                && value.regionMatches(true, 0, route, 0, length)
                && Character.isWhitespace(value.charAt(length));
    }

    private record NestedCommand(List<RegisteredSection> parentSections, Command command) {
    }

    private record CommandMatch(List<RegisteredSection> parentSections, Command command) {
        private int routeLength() {
            return parentSections.size() + 1;
        }

        private String primaryAlias() {
            return command.getAliases().getFirst();
        }

        private String canonicalRoute() {
            if (parentSections.isEmpty()) return primaryAlias();
            return String.join(" ", parentPath()) + " " + primaryAlias();
        }

        private List<String> canonicalPath() {
            List<String> path = new ArrayList<>(parentSections.size() + 1);
            path.addAll(parentPath());
            path.add(primaryAlias());
            return path;
        }

        private List<String> parentPath() {
            return CommandManager.canonicalPath(parentSections);
        }

        private boolean matches(String[] args) {
            if (args.length < routeLength()) return false;
            for (int i = 0; i < parentSections.size(); i++) {
                if (!parentSections.get(i).matches(args[i])) return false;
            }
            return matchesAlias(command, args[parentSections.size()]);
        }

        private boolean matchesSegment(int index, String value) {
            if (index < parentSections.size()) return parentSections.get(index).matches(value);
            return index == parentSections.size() && matchesAlias(command, value);
        }

        private List<String> aliasesAt(int index) {
            if (index < parentSections.size()) return parentSections.get(index).aliases();
            if (index == parentSections.size()) return command.getAliases();
            return List.of();
        }

        private String[] localArgs(String[] args) {
            return Arrays.copyOfRange(args, parentSections.size(), args.length);
        }
    }

    private static final class RegisteredSection {
        private final List<RegisteredSection> parentSections;
        private final List<String> path;
        private final List<String> aliases;
        private String permission = "";
        private boolean consoleAllowed = true;

        private RegisteredSection(
                List<RegisteredSection> parentSections,
                List<String> path,
                List<String> aliases
        ) {
            this.parentSections = parentSections;
            this.path = path;
            this.aliases = aliases;
        }

        private String primaryAlias() {
            return aliases.getFirst();
        }

        private List<String> aliases() {
            return aliases;
        }

        private boolean matches(String value) {
            return aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(value));
        }

        private boolean matches(String[] args) {
            if (args.length < path.size()) return false;
            for (int i = 0; i < parentSections.size(); i++) {
                if (!parentSections.get(i).matches(args[i])) return false;
            }
            return matches(args[parentSections.size()]);
        }
    }
}
