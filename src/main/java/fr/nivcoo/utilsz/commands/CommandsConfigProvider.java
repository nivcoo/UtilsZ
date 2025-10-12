package fr.nivcoo.utilsz.commands;

import java.util.List;

public interface CommandsConfigProvider {
    String noPermission();
    String incorrectUsage();
    List<String> help();
}