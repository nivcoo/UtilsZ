package fr.nivcoo.utilsz.commands;

import fr.nivcoo.utilsz.config.Config;

import java.util.List;

public final class PathCommandsConfigProvider implements CommandsConfigProvider {
    private final Config cfg;
    private final String noPermPath;
    private final String incorrectPath;
    private final String helpPath;

    public PathCommandsConfigProvider(Config cfg,
                                      String noPermissionPath,
                                      String incorrectUsagePath,
                                      String helpMessagesPath) {
        this.cfg = cfg;
        this.noPermPath = noPermissionPath;
        this.incorrectPath = incorrectUsagePath;
        this.helpPath = helpMessagesPath;
    }

    @Override public String noPermission()   { return cfg.getString(noPermPath); }
    @Override public String incorrectUsage() { return cfg.getString(incorrectPath); }
    @Override public List<String> help()     { return cfg.getStringList(helpPath); }
}

