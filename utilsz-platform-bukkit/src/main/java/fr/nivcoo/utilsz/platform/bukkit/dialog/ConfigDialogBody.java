package fr.nivcoo.utilsz.platform.bukkit.dialog;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

@SuppressWarnings("unused")
public class ConfigDialogBody {

    public ConfigDialogBodyType type = ConfigDialogBodyType.TEXT;
    public Component text = ConfigManager.parseDynamic("&7Texte");
    public int width = 300;
    public ConfigItem item = new ConfigItem(Material.STONE, "&fItem", null);
    public boolean showDecorations = true;
    public boolean showTooltip = true;
    public int itemWidth = 16;
    public int itemHeight = 16;
    public Component itemDescription = null;
    public int itemDescriptionWidth = 300;
}
