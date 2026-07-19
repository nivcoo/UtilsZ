package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public final class ConfiguredGui {

    private final ConfigGuiRegistry registry;
    private final String id;
    private final String inventoryKey;

    ConfiguredGui(ConfigGuiRegistry registry, String id, String inventoryKey) {
        this.registry = registry;
        this.id = id;
        this.inventoryKey = inventoryKey;
    }

    public String id() {
        return id;
    }

    public int rows(GuiInventory inventory) {
        return resolve(inventory).rows();
    }

    public Component title(GuiInventory inventory) {
        return title(inventory, Map.of());
    }

    public Component title(GuiInventory inventory, Map<String, ?> placeholders) {
        return ConfigManager.fmt(resolve(inventory).title(), safePlaceholders(placeholders));
    }

    public ConfigGuiView view(GuiInventory inventory) {
        return view(inventory, Map.of());
    }

    public ConfigGuiView view(GuiInventory inventory, Map<String, ?> placeholders) {
        return new ConfigGuiView(inventory, resolve(inventory), registry.logger(), placeholders);
    }

    public long revision(GuiInventory inventory) {
        return resolve(inventory).revision();
    }

    private ResolvedConfigGuiMenu resolve(GuiInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        Object pinned = inventory.get(inventoryKey);
        if (pinned instanceof ResolvedConfigGuiMenu menu && id.equals(menu.id())) return menu;
        ResolvedConfigGuiMenu current = registry.resolve(id);
        inventory.put(inventoryKey, current);
        return current;
    }

    private static Map<String, ?> safePlaceholders(Map<String, ?> placeholders) {
        return placeholders == null ? Map.of() : placeholders;
    }
}
