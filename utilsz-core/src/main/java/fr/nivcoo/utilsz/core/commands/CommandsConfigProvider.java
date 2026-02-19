package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;
import java.util.List;

public interface CommandsConfigProvider {
    Component noPermission();
    Component incorrectUsage();
    List<Component> help();
}
