package fr.nivcoo.utilsz.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

public class ClickableItem {

    private final ItemStack item;
    private final Consumer<InventoryClickEvent> event;

    private ClickableItem(ItemStack item, Consumer<InventoryClickEvent> event) {
        this.item = item;
        this.event = event != null ? event : e -> {
        };
    }

    public void run(InventoryClickEvent e) {
        event.accept(e);
    }

    public ItemStack getItemStack() {
        return item;
    }

    public static ClickableItem of(ItemStack is) {
        return new ClickableItem(is, null);
    }

    public static ClickableItem of(ItemStack is, Consumer<InventoryClickEvent> event) {
        return new ClickableItem(is, event);
    }

    public static ClickableItem empty(ItemStack is) {
        return new ClickableItem(is, e -> e.setCancelled(true));
    }

    public ClickableItem cloneItem() {
        return new ClickableItem(item.clone(), event);
    }
}
