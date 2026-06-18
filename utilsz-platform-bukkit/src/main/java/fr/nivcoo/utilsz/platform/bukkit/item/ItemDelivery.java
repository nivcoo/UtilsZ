package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemDelivery {
    private ItemDelivery() {
    }

    public static List<ItemStack> giveOrDrop(Player player, ItemStack... items) {
        List<ItemStack> leftovers = new ArrayList<>();
        if (player == null || items == null || items.length == 0) return leftovers;

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items);
        for (ItemStack item : overflow.values()) {
            if (item == null || item.getType().isAir()) continue;
            leftovers.add(item);
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        return leftovers;
    }
}
