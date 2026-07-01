package fr.nivcoo.utilsz.platform.bukkit.gui;

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

    public ItemStack item(ConfigGuiItem item, T context, int page) {
        if (item == null || !item.enabled) return new ItemStack(Material.AIR);
        ItemStack stack = ConfigItemFactory.create(ConfigItemFactory.format(item, placeholders(context, page)), logger);
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }

    public void set(GuiInventory inv, ConfigGuiItem item, T context, int page) {
        set(inv, item, context, page, event -> {});
    }

    public void set(GuiInventory inv, ConfigGuiItem item, T context, int page, Consumer<InventoryClickEvent> click) {
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

    public List<Integer> slots(ConfigGuiItem item) {
        if (item == null) return List.of();
        if (item.slots != null && !item.slots.isEmpty()) return item.slots;
        return item.slot == null ? List.of() : List.of(item.slot);
    }

    public List<Integer> slots(ConfigGuiItem item, int inventorySize) {
        return slots(item).stream().filter(slot -> slot >= 0 && slot < inventorySize).toList();
    }

    public List<Integer> slotsOrRange(ConfigGuiItem item, int inventorySize, int fallbackStart, int fallbackEndExclusive) {
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
