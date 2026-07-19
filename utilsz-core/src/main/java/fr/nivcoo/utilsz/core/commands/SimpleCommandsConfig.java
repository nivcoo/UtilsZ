package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;
import java.util.List;

public record SimpleCommandsConfig(
        Component noPermission,
        Component incorrectUsage,
        Component playerOnly,
        List<Component> help
)
        implements CommandsConfigProvider {
    public SimpleCommandsConfig(Component noPermission, Component incorrectUsage, List<Component> help) {
        this(noPermission, incorrectUsage, noPermission, help);
    }

    @Override public Component noPermission() { return noPermission; }
    @Override public Component incorrectUsage() { return incorrectUsage; }
    @Override public Component playerOnly() { return playerOnly; }
    @Override public List<Component> help() { return help; }
}
