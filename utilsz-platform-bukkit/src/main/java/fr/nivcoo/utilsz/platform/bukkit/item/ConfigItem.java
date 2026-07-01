package fr.nivcoo.utilsz.platform.bukkit.item;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Name;
import fr.nivcoo.utilsz.core.config.annotations.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class ConfigItem {

    public Material material = Material.STONE;
    @Optional
    public String texture = "";
    @Comment("UUID du propriétaire de la tête joueur. Ignoré si texture est renseignée.")
    @Optional
    @Name("skull_owner")
    public String skullOwner = "";
    public Component name = null;
    public List<Component> lore = List.of();
    @Optional
    public Map<String, Integer> enchants = null;
    @Optional
    public List<ItemFlag> flags = List.of();
    @Optional
    public Boolean glow = null;
    @Optional
    @Name("custom_model_data")
    public int customModelData = 0;

    public ConfigItem() {
    }

    public ConfigItem(Material material, String name, List<String> lore) {
        this.material = material;
        this.name = ConfigManager.parseDynamic(name);
        this.lore = lore == null ? List.of() : lore.stream().map(ConfigManager::parseDynamic).toList();
    }
}
