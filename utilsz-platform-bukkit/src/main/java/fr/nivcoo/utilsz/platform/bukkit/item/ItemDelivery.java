package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("unused")
public final class ItemDelivery {
    public static final int MAX_DELIVERY_AMOUNT = 10_000;

    private ItemDelivery() {
    }

    public static HashMap<Integer, ItemStack> give(Player player, ItemStack... items) {
        if (player == null || items == null || items.length == 0) return new HashMap<>();
        return new HashMap<>(player.getInventory().addItem(items));
    }

    public static HashMap<Integer, ItemStack> give(Player player, ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return new HashMap<>();
        return give(player, split(item, amount).toArray(ItemStack[]::new));
    }

    public static void giveOrDrop(Player player, ItemStack... items) {
        if (player == null || items == null || items.length == 0) return;

        for (ItemStack item : give(player, items).values()) {
            if (item == null || item.getType().isAir()) continue;
            drop(player.getLocation(), item);
        }
    }

    public static void giveOrDrop(Player player, ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return;
        giveOrDrop(player, split(item, amount).toArray(ItemStack[]::new));
    }

    public static void drop(Location location, ItemStack... items) {
        if (location == null || location.getWorld() == null || items == null || items.length == 0) return;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            location.getWorld().dropItemNaturally(location, item);
        }
    }

    public static void drop(Location location, ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return;
        drop(location, split(item, amount).toArray(ItemStack[]::new));
    }

    public static void dropAtBlock(Location location, ItemStack... items) {
        if (location == null) return;
        drop(location.clone().add(0.5, 0.5, 0.5), items);
    }

    public static void dropAtBlock(Location location, ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return;
        dropAtBlock(location, split(item, amount).toArray(ItemStack[]::new));
    }

    private static List<ItemStack> split(ItemStack item, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        if (item == null || item.getType().isAir()) return stacks;

        int remaining = Math.max(1, Math.min(amount, MAX_DELIVERY_AMOUNT));
        int stackSize = Math.max(1, item.getMaxStackSize());

        while (remaining > 0) {
            int next = Math.min(stackSize, remaining);
            ItemStack stack = item.clone();
            stack.setAmount(next);
            stacks.add(stack);
            remaining -= next;
        }

        return stacks;
    }
}
