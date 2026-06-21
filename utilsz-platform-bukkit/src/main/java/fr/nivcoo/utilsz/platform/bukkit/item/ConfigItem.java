package fr.nivcoo.utilsz.platform.bukkit.item;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.config.annotations.Name;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigItem {

    public Material material = Material.STONE;
    public int amount = 1;
    public String texture = "";
    public Component name = null;
    public List<Component> lore = List.of();
    public Map<String, Integer> enchantments = new LinkedHashMap<>();
    public List<ItemFlag> flags = List.of();
    public boolean glow = false;
    @Name("custom_model_data")
    public int customModelData = 0;

    public ConfigItem() {
    }

    public ConfigItem(Material material, int amount, String name, List<String> lore) {
        this.material = material;
        this.amount = amount;
        this.name = ConfigManager.parseDynamic(name);
        this.lore = lore == null ? List.of() : lore.stream().map(ConfigManager::parseDynamic).toList();
    }
}
