package fr.nivcoo.utilsz.core.commands;

import java.util.List;

@SuppressWarnings("unused")
public final class CommandSection {

    private final CommandManager manager;
    private final List<String> path;

    CommandSection(CommandManager manager, List<String> path) {
        this.manager = manager;
        this.path = path;
    }

    public CommandSection addSection(String name, String... aliases) {
        return manager.addSection(path, name, aliases);
    }

    public CommandSection addCommand(Command command) {
        manager.addCommand(path, command);
        return this;
    }

    public CommandSection permission(String permission) {
        manager.setSectionPermission(path, permission);
        return this;
    }

    public CommandSection canBeExecutedByConsole(boolean allowed) {
        manager.setSectionConsoleAllowed(path, allowed);
        return this;
    }

    public String getPath() {
        return String.join(" ", path);
    }
}
