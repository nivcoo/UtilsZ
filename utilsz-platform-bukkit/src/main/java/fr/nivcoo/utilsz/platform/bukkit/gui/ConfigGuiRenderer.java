package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItem;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItemFactory;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public final class ConfigGuiRenderer<T> {

    private final Logger logger;
    private final BiFunction<T, Integer, Map<String, Object>> placeholders;

    public ConfigGuiRenderer(Logger logger, BiFunction<T, Integer, Map<String, Object>> placeholders) {
        this.logger = logger;
        this.placeholders = placeholders;
    }

    public ItemStack item(ConfigItem item, T context, int page) {
        if (item == null || !item.enabled) return new ItemStack(Material.AIR);
        ConfigItem copy = new ConfigItem();
        copy.enabled = item.enabled;
        copy.slot = item.slot;
        copy.slots = item.slots == null ? List.of() : List.copyOf(item.slots);
        copy.material = item.material;
        copy.amount = item.amount;
        copy.texture = item.texture;
        copy.skullOwner = item.skullOwner;
        copy.name = ConfigManager.fmt(item.name, placeholders(context, page));
        copy.lore = item.lore == null ? List.of() : item.lore.stream()
                .map(line -> ConfigManager.fmt(line, placeholders(context, page)))
                .toList();
        copy.enchants = item.enchants;
        copy.flags = item.flags == null ? List.of() : List.copyOf(item.flags);
        copy.glow = item.glow;
        copy.customModelData = item.customModelData;
        ItemStack stack = ConfigItemFactory.create(copy, logger);
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }

    public void set(GuiInventory inv, ConfigItem item, T context, int page) {
        set(inv, item, context, page, event -> {});
    }

    public void set(GuiInventory inv, ConfigItem item, T context, int page, Consumer<InventoryClickEvent> click) {
        if (item == null || !item.enabled) return;
        ItemStack stack = item(item, context, page);
        for (int slot : slots(item, inv.getBukkitInventory().getSize())) {
            inv.set(slot, ClickableItem.of(stack.clone(), click));
        }
    }

    public void clear(GuiInventory inv) {
        ItemStack air = new ItemStack(Material.AIR);
        for (int slot = 0; slot < inv.getBukkitInventory().getSize(); slot++) {
            inv.set(slot, ClickableItem.of(air));
        }
    }

    public List<Integer> slots(ConfigItem item) {
        if (item == null) return List.of();
        if (item.slots != null && !item.slots.isEmpty()) return item.slots;
        return item.slot == null ? List.of() : List.of(item.slot);
    }

    public List<Integer> slots(ConfigItem item, int inventorySize) {
        return slots(item).stream().filter(slot -> slot >= 0 && slot < inventorySize).toList();
    }

    public List<Integer> slotsOrRange(ConfigItem item, int inventorySize, int fallbackStart, int fallbackEndExclusive) {
        List<Integer> configured = slots(item, inventorySize);
        if (!configured.isEmpty()) return configured;
        int safeStart = Math.max(0, fallbackStart);
        int safeEnd = Math.min(inventorySize, fallbackEndExclusive);
        if (safeStart >= safeEnd) return List.of();
        return IntStream.range(safeStart, safeEnd).boxed().toList();
    }

    public Map<String, Object> placeholders(T context, int page) {
        return placeholders.apply(context, page);
    }
}
