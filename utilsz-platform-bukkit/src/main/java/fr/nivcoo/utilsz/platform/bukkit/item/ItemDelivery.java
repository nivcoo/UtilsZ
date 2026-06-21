package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class ItemDelivery {
    public static final int MAX_DELIVERY_AMOUNT = 10_000;

    private ItemDelivery() {
    }

    public static List<ItemStack> giveOrDrop(Player player, ItemStack... items) {
        List<ItemStack> leftovers = new ArrayList<>();
        if (player == null || items == null || items.length == 0) return leftovers;

        Map<Integer, ItemStack> overflow = addToInventory(player, items);
        for (ItemStack item : overflow.values()) {
            if (item == null || item.getType().isAir()) continue;
            leftovers.add(item);
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        return leftovers;
    }

    public static List<ItemStack> giveOrDrop(Player player, ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return List.of();
        return giveOrDrop(player, split(item, amount).toArray(ItemStack[]::new));
    }

    public static HashMap<Integer, ItemStack> addToInventory(Player player, ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return new HashMap<>();
        return addToInventory(player, split(item, amount).toArray(ItemStack[]::new));
    }

    public static HashMap<Integer, ItemStack> addToInventory(Player player, ItemStack... items) {
        if (player == null || items == null || items.length == 0) return new HashMap<>();
        return new HashMap<>(player.getInventory().addItem(items));
    }

    public static List<ItemStack> split(ItemStack item, int amount) {
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
