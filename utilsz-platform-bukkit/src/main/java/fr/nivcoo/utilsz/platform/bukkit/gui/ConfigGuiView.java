package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItemFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class ConfigGuiView {

    private final GuiInventory inventory;
    private final ResolvedConfigGuiMenu menu;
    private final Logger logger;
    private final Map<String, Object> placeholders;
    private final ConfigGuiRenderer<Map<String, Object>> renderer;

    ConfigGuiView(
            GuiInventory inventory,
            ResolvedConfigGuiMenu menu,
            Logger logger,
            Map<String, ?> placeholders
    ) {
        this.inventory = inventory;
        this.menu = menu;
        this.logger = logger;
        this.placeholders = copyPlaceholders(placeholders);
        this.renderer = new ConfigGuiRenderer<>(logger, (values, ignoredPage) -> values);
    }

    public ConfigGuiView begin() {
        Set<Integer> excluded = inventory.getExcludeCases() == null
                ? Set.of() : new HashSet<>(inventory.getExcludeCases());
        for (int slot = 0; slot < inventory.getBukkitInventory().getSize(); slot++) {
            if (!excluded.contains(slot)) inventory.clear(slot);
        }
        for (ConfigGuiItem customItem : menu.customItems().values()) {
            ConfigGuiItem visible = withoutExcludedSlots(customItem, excluded);
            if (visible != null) passive(visible);
        }
        return this;
    }

    public ConfigGuiView with(Map<String, ?> additionalPlaceholders) {
        Map<String, Object> merged = new LinkedHashMap<>(placeholders);
        if (additionalPlaceholders != null) additionalPlaceholders.forEach(merged::put);
        return new ConfigGuiView(inventory, menu, logger, merged);
    }

    public Set<String> itemIds() {
        return menu.items().keySet();
    }

    public Map<String, Object> placeholders() {
        return placeholders;
    }

    public ConfigGuiItem definition(String id) {
        ConfigGuiItem item = menu.items().get(normalizeItemId(id));
        if (item == null) throw new IllegalArgumentException("Unknown item '" + id + "' in menu '" + menu.id() + "'");
        return (ConfigGuiItem) ConfigItemFactory.copy(item);
    }

    public ItemStack stack(String id) {
        return renderer.item(definition(id), placeholders, 0);
    }

    public Component format(Component component) {
        return ConfigManager.fmt(component, placeholders);
    }

    public List<Integer> region(String id) {
        List<Integer> slots = menu.regions().get(normalizeItemId(id));
        if (slots == null) throw new IllegalArgumentException("Unknown region '" + id + "' in menu '" + menu.id() + "'");
        return slots;
    }

    public void clearRegion(String id) {
        clear(region(id));
    }

    public void clearItem(String id) {
        clear(slots(definition(id)));
    }

    public void clear(int slot) {
        inventory.clear(slot);
    }

    public void clear(Collection<Integer> slots) {
        if (slots == null) return;
        for (Integer slot : slots) {
            if (slot != null) inventory.clear(slot);
        }
    }

    public void passive(String id) {
        passive(definition(id));
    }

    public void passive(String id, int slot) {
        passive(atSlot(definition(id), slot));
    }

    public void passive(ConfigGuiItem item) {
        renderer.set(inventory, item, placeholders, 0);
    }

    public void passive(ConfigGuiItem item, int slot) {
        passive(atSlot(item, slot));
    }

    public void action(String id, Consumer<InventoryClickEvent> click) {
        action(definition(id), click);
    }

    public void action(String id, int slot, Consumer<InventoryClickEvent> click) {
        action(atSlot(definition(id), slot), click);
    }

    public void action(ConfigGuiItem item, Consumer<InventoryClickEvent> click) {
        renderer.set(inventory, item, placeholders, 0, click == null ? ignored -> { } : click);
    }

    public void action(ConfigGuiItem item, int slot, Consumer<InventoryClickEvent> click) {
        action(atSlot(item, slot), click);
    }

    public void stack(int slot, ItemStack stack) {
        stack(slot, stack, null);
    }

    public void stack(int slot, ItemStack stack, Consumer<InventoryClickEvent> click) {
        if (stack == null || stack.getType().isAir()) {
            inventory.clear(slot);
            return;
        }
        ItemStack copy = stack.clone();
        inventory.set(slot, click == null ? ClickableItem.of(copy) : ClickableItem.of(copy, click));
    }

    private static ConfigGuiItem atSlot(ConfigGuiItem source, int slot) {
        ConfigGuiItem copy = (ConfigGuiItem) ConfigItemFactory.copy(source);
        copy.slot = slot;
        copy.slots = List.of();
        return copy;
    }

    static ConfigGuiItem withoutExcludedSlots(ConfigGuiItem source, Set<Integer> excluded) {
        List<Integer> visible = slots(source).stream()
                .filter(slot -> !excluded.contains(slot))
                .toList();
        if (visible.isEmpty()) return null;
        ConfigGuiItem copy = (ConfigGuiItem) ConfigItemFactory.copy(source);
        if (source.slots != null && !source.slots.isEmpty()) {
            copy.slot = null;
            copy.slots = visible;
        } else {
            copy.slot = visible.getFirst();
            copy.slots = List.of();
        }
        return copy;
    }

    private static List<Integer> slots(ConfigGuiItem item) {
        if (item.slots != null && !item.slots.isEmpty()) return item.slots;
        return item.slot == null ? List.of() : List.of(item.slot);
    }

    private static String normalizeItemId(String id) {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        return id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, Object> copyPlaceholders(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source != null) source.forEach(copy::put);
        return java.util.Collections.unmodifiableMap(copy);
    }
}
