package fr.nivcoo.utilsz.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

public class InventoryManager implements Listener {

    private final JavaPlugin javaPlugin;
    private final HashMap<UUID, Inventory> inventories;

    public InventoryManager(JavaPlugin javaPlugin) {
        this.inventories = new HashMap<>();
        this.javaPlugin = javaPlugin;
    }

    public void init() {
        Bukkit.getPluginManager().registerEvents(this, javaPlugin);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(javaPlugin, () -> {
            if (inventories.isEmpty()) return;
            for (Inventory inv : inventories.values()) {
                int tick = 0;
                Object currentTick = inv.get(Inventory.TICK);
                if (currentTick instanceof Integer) tick = Integer.parseInt(currentTick.toString());
                tick++;
                inv.put(Inventory.TICK, tick);

                InventoryProvider p = inv.getInventoryProvider();
                int period = Math.max(1, p.updatePeriodTicks());
                if (tick % period == 0 && p.needsUpdate(inv)) {
                    p.update(inv);
                }
            }
        }, 1, 1);
    }

    public Inventory openInventory(InventoryProvider provider, Player p) {
        return openInventory(provider, p, null);
    }

    public Inventory openInventory(InventoryProvider provider, Player p, Consumer<Inventory> params) {
        Inventory inv = new Inventory(p, provider, params);
        provider.init(inv);
        inventories.put(p.getUniqueId(), inv);
        inv.open();
        return inv;
    }

    public Inventory getInventory(Player p) {
        return inventories.get(p.getUniqueId());
    }

    public Inventory getInventory(UUID uuid) {
        return inventories.get(uuid);
    }

    public Collection<Inventory> getInventories() {
        return inventories.values();
    }

    public boolean hasInventoryOpened(Player p) {
        return inventories.containsKey(p.getUniqueId());
    }

    public void closeInventory(Player p) {
        p.closeInventory();
    }

    public void closeAllInventories() {
        for (Inventory inv : inventories.values()) inv.getPlayer().closeInventory();
    }

    @EventHandler
    public void onPlayerInventoryClick(InventoryClickEvent e) {
        if (!inventories.containsKey(e.getWhoClicked().getUniqueId())) return;

        Player p = (Player) e.getWhoClicked();
        Inventory inv = getInventory(p);
        if (inv == null) return;

        int topSize = e.getView().getTopInventory().getSize();
        boolean isTop = e.getRawSlot() < topSize;

        InventoryProvider provider = inv.getInventoryProvider();
        if (isTop) {
            if (inv.getExcludeCases() == null || !inv.getExcludeCases().contains(e.getSlot())) {
                e.setCancelled(true);
            }
        } else {
            if (provider.cancelBottomClicks()) {
                e.setCancelled(true);
            }
        }

        org.bukkit.inventory.Inventory clickedInventory = e.getClickedInventory();
        if (clickedInventory == null) return;
        if (!inv.getBukkitInventory().equals(clickedInventory)) return;

        if (!isTop && !e.isShiftClick()) return;

        inv.handler(e);
    }

    @EventHandler
    public void onPlayerInventoryDrag(InventoryDragEvent e) {
        if (!inventories.containsKey(e.getWhoClicked().getUniqueId())) return;

        Inventory inv = getInventory((Player) e.getWhoClicked());
        if (inv == null) return;

        boolean cancelTopDrag = inv.getInventoryProvider().cancelTopDrag();
        if (!cancelTopDrag) {
            e.setCancelled(false);
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        e.setCancelled(touchesTop);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInventoryClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        Inventory inv = inventories.get(uuid);
        if (inv == null) return;

        if (!inv.getBukkitInventory().equals(e.getInventory())) return;

        InventoryProvider provider = inv.getInventoryProvider();

        if (!provider.allowClose(inv)) {
            Bukkit.getScheduler().runTask(javaPlugin, inv::open);
            return;
        }

        inventories.remove(uuid);
        provider.onClose(e, inv);
    }
}
