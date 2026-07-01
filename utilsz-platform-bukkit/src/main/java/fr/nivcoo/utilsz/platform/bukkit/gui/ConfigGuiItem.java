package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Optional;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItem;
import org.bukkit.Material;

import java.util.List;

@SuppressWarnings("unused")
public class ConfigGuiItem extends ConfigItem {

    public int amount = 1;
    @Optional
    public boolean enabled = true;
    @Comment("Slot unique 0-53.")
    @Optional
    public Integer slot = null;
    @Comment("Slots multiples 0-53 pour dupliquer le même item.")
    @Optional
    public List<Integer> slots = List.of();

    public ConfigGuiItem() {
    }

    public ConfigGuiItem(Material material, int amount, String name, List<String> lore) {
        super(material, name, lore);
        this.amount = amount;
    }
}
