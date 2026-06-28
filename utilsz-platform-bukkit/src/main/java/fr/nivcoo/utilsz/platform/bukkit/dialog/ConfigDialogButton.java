package fr.nivcoo.utilsz.platform.bukkit.dialog;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;

@SuppressWarnings("unused")
public class ConfigDialogButton {

    public Component label = ConfigManager.parseDynamic("&aValider");
    public Component tooltip = null;
    public int width = 150;
    public String actionId = "";
    public String commandTemplate = "";
}
