package fr.nivcoo.utilsz.commands;

import net.kyori.adventure.text.Component;

import java.util.List;

public record SimpleCommandsConfig(Component noPermission, Component incorrectUsage, List<Component> help)
        implements CommandsConfigProvider {

    @Override
    public Component noPermission() {
        return noPermission;
    }

    @Override
    public Component incorrectUsage() {
        return incorrectUsage;
    }

    @Override
    public List<Component> help() {
        return help;
    }
}
