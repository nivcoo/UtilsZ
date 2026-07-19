package fr.nivcoo.utilsz.platform.bukkit.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class GuiInventoryManager implements Listener {

    private final JavaPlugin plugin;
    private final HashMap<UUID, GuiInventory> inventories;
    private final HashMap<UUID, GuiInventory> pendingOpens;

    public GuiInventoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.inventories = new HashMap<>();
        this.pendingOpens = new HashMap<>();
    }

    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (inventories.isEmpty()) return;

            var iterator = inventories.values().iterator();
            while (iterator.hasNext()) {
                GuiInventory inv = iterator.next();
                if (pendingOpens.containsKey(inv.getPlayer().getUniqueId())) continue;
                if (!isViewing(inv.getPlayer(), inv)) {
                    iterator.remove();
                    continue;
                }

                int tick = 0;
                Object currentTick = inv.get(GuiInventory.TICK);
                if (currentTick instanceof Integer) tick = (Integer) currentTick;
                else if (currentTick != null) {
                    try { tick = Integer.parseInt(currentTick.toString()); } catch (Exception ignored) {}
                }

                tick++;
                inv.put(GuiInventory.TICK, tick);

                GuiProvider p = inv.getProvider();
                int period = Math.max(1, p.updatePeriodTicks());
                if (inv.consumeRefreshRequest()) {
                    p.refresh(inv);
                } else if (tick % period == 0 && p.needsUpdate(inv)) {
                    p.update(inv);
                }
            }
        }, 1, 1);
    }

    public GuiInventory open(GuiProvider provider, Player p) {
        return open(provider, p, null);
    }

    public GuiInventory open(GuiProvider provider, Player p, Consumer<GuiInventory> params) {
        GuiInventory inv = new GuiInventory(p, provider, params);
        provider.init(inv);
        inventories.put(p.getUniqueId(), inv);
        if (shouldDeferOpen(p, inv)) {
            UUID uuid = p.getUniqueId();
            pendingOpens.put(uuid, inv);
            p.closeInventory();
            return inv;
        }
        inv.open();
        return inv;
    }

    public GuiInventory get(Player p) {
        return inventories.get(p.getUniqueId());
    }

    public GuiInventory get(UUID uuid) {
        return inventories.get(uuid);
    }

    public Collection<GuiInventory> getInventories() {
        return inventories.values();
    }

    public boolean hasOpened(Player p) {
        return inventories.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        p.closeInventory();
    }

    public void closeAll() {
        for (GuiInventory inv : inventories.values()) inv.getPlayer().closeInventory();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        UUID uuid = e.getWhoClicked().getUniqueId();
        if (!inventories.containsKey(uuid)) return;

        Player p = (Player) e.getWhoClicked();
        GuiInventory inv = get(p);
        if (inv == null) return;
        if (!e.getView().getTopInventory().equals(inv.getBukkitInventory())) {
            if (!pendingOpens.containsKey(uuid) && !isViewing(p, inv)) inventories.remove(uuid);
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        boolean isTop = e.getRawSlot() < topSize;

        GuiProvider provider = inv.getProvider();
        if (isTop) {
            if (inv.getExcludeCases() == null || !inv.getExcludeCases().contains(e.getSlot())) {
                e.setCancelled(true);
            }
        } else {
            if (provider.cancelBottomClicks()) {
                e.setCancelled(true);
            }
        }

        if (!isTop) return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        if (!inv.getBukkitInventory().equals(clicked)) return;
        inv.handleClick(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        UUID uuid = e.getWhoClicked().getUniqueId();
        if (!inventories.containsKey(uuid)) return;

        Player p = (Player) e.getWhoClicked();
        GuiInventory inv = get(p);
        if (inv == null) return;
        if (!e.getView().getTopInventory().equals(inv.getBukkitInventory())) {
            if (!pendingOpens.containsKey(uuid) && !isViewing(p, inv)) inventories.remove(uuid);
            return;
        }

        boolean cancelTopDrag = inv.getProvider().cancelTopDrag();
        if (!cancelTopDrag) {
            e.setCancelled(false);
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        e.setCancelled(touchesTop);
    }

    private boolean isViewing(Player player, GuiInventory inv) {
        return player != null
                && player.isOnline()
                && inv != null
                && player.getOpenInventory().getTopInventory().equals(inv.getBukkitInventory());
    }

    private boolean shouldDeferOpen(Player player, GuiInventory next) {
        if (player == null || !player.isOnline()) return false;
        Inventory current = player.getOpenInventory().getTopInventory();
        return current.getType() != InventoryType.CRAFTING
                && !current.equals(next.getBukkitInventory())
                && !isManagedInventory(current);
    }

    private boolean isManagedInventory(Inventory inventory) {
        if (inventory == null) return false;
        if (inventory.getHolder() instanceof GuiInventory) return true;
        if (inventory.getHolder() instanceof ManagedGuiInventoryHolder holder && holder.isManagedGuiInventory()) return true;
        return inventories.values().stream().anyMatch(inv -> inventory.equals(inv.getBukkitInventory()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        GuiInventory pending = pendingOpens.get(uuid);
        if (pending != null && !pending.getBukkitInventory().equals(e.getInventory())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pendingOpens.get(uuid) != pending) return;
                pendingOpens.remove(uuid);
                if (pending.getPlayer().isOnline() && inventories.get(uuid) == pending) {
                    pending.open();
                }
            }, 4L);
        }

        GuiInventory inv = inventories.get(uuid);
        if (inv == null) return;

        if (!inv.getBukkitInventory().equals(e.getInventory())) return;

        GuiProvider provider = inv.getProvider();

        if (!provider.allowClose(inv)) {
            Bukkit.getScheduler().runTask(plugin, inv::open);
            return;
        }

        inventories.remove(uuid);
        provider.onClose(e, inv);
    }
}
