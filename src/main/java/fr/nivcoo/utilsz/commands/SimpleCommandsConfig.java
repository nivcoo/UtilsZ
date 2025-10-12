package fr.nivcoo.utilsz.commands;

import java.util.List;

public record SimpleCommandsConfig(String noPermission, String incorrectUsage, List<String> help)
        implements CommandsConfigProvider {

    @Override public String noPermission()   { return noPermission; }
    @Override public String incorrectUsage() { return incorrectUsage; }
    @Override public List<String> help()     { return help; }
}
