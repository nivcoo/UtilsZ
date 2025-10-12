// PathCommandsConfigProvider.java
package fr.nivcoo.utilsz.commands;

import fr.nivcoo.utilsz.config.Config;
import fr.nivcoo.utilsz.config.ConfigManager;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public final class PathCommandsConfigProvider implements CommandsConfigProvider {
    private final Config cfg;
    private String noPermPath;
    private String incorrectPath;
    private String helpPath;

    public PathCommandsConfigProvider(Config cfg,
                                      String noPermissionPath,
                                      String incorrectUsagePath,
                                      String helpMessagesPath) {
        this.cfg = cfg;
        this.noPermPath = noPermissionPath;
        this.incorrectPath = incorrectUsagePath;
        this.helpPath = helpMessagesPath;
    }

    @Override
    public Component noPermission() {
        String s = cfg.getString(noPermPath);
        return s == null ? Component.empty() : ConfigManager.parseDynamic(s);
    }

    @Override
    public Component incorrectUsage() {
        String s = cfg.getString(incorrectPath);
        return s == null ? Component.empty() : ConfigManager.parseDynamic(s);
    }

    @Override
    public List<Component> help() {
        List<String> list = cfg.getStringList(helpPath);
        if (list == null || list.isEmpty()) return List.of();
        List<Component> out = new ArrayList<>(list.size());
        for (String s : list) out.add(ConfigManager.parseDynamic(s == null ? "" : s));
        return out;
    }

    public void setNoPermissionPath(String p)   { this.noPermPath = p; }
    public void setIncorrectUsagePath(String p) { this.incorrectPath = p; }
    public void setHelpPath(String p)           { this.helpPath = p; }
}
