package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
        for (GuiInventory inv : List.copyOf(inventories.values())) inv.getPlayer().closeInventory();
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
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        if (isTop) {
            if (!editableTopSlot(inv.getEditableSlots().slots(),
                    inv.isManagedSlot(e.getSlot()), e.getSlot())) {
                e.setCancelled(true);
            } else if (!e.isCancelled() && !accepts(inv, incomingTopItem(e, p))) {
                e.setCancelled(true);
            }
        } else {
            if (provider.cancelBottomClicks()) {
                e.setCancelled(true);
            } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (!e.isCancelled() && accepts(inv, e.getCurrentItem())) {
                    moveToEditableTop(inv, e.getClickedInventory(), e.getSlot());
                }
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

        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesLockedTop = e.getRawSlots().stream()
                .filter(raw -> raw < topSize)
                .anyMatch(raw -> !inv.getEditableSlots().dragAllowed()
                        || !editableTopSlot(inv.getEditableSlots().slots(),
                        inv.isManagedSlot(raw), raw));
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (touchesLockedTop || !e.isCancelled() && touchesTop
                && !accepts(inv, e.getOldCursor())) e.setCancelled(true);
    }

    static boolean editableTopSlot(
            Collection<Integer> editableSlots,
            boolean managed,
            int slot
    ) {
        return !managed && editableSlots != null && editableSlots.contains(slot);
    }

    private static boolean moveToEditableTop(GuiInventory inv, Inventory sourceInventory, int sourceSlot) {
        if (sourceInventory == null || sourceSlot < 0 || sourceSlot >= sourceInventory.getSize()) return false;
        Collection<Integer> editableSlots = inv.getEditableSlots().slots();
        if (editableSlots == null || editableSlots.isEmpty()) return false;
        ItemStack source = sourceInventory.getItem(sourceSlot);
        if (source == null || source.getType().isAir() || source.getAmount() <= 0) return false;
        Inventory top = inv.getBukkitInventory();
        int remaining = source.getAmount();
        for (Integer slot : editableSlots) {
            if (remaining <= 0) break;
            if (slot == null || slot < 0 || slot >= top.getSize() || inv.isManagedSlot(slot)) continue;
            ItemStack target = top.getItem(slot);
            if (target == null || target.getType().isAir() || !target.isSimilar(source)) continue;
            int capacity = target.getMaxStackSize() - target.getAmount();
            if (capacity <= 0) continue;
            int moved = Math.min(capacity, remaining);
            target.setAmount(target.getAmount() + moved);
            top.setItem(slot, target);
            remaining -= moved;
        }
        for (Integer slot : editableSlots) {
            if (remaining <= 0) break;
            if (slot == null || slot < 0 || slot >= top.getSize() || inv.isManagedSlot(slot)) continue;
            ItemStack target = top.getItem(slot);
            if (target != null && !target.getType().isAir()) continue;
            ItemStack moved = source.clone();
            moved.setAmount(Math.min(source.getMaxStackSize(), remaining));
            top.setItem(slot, moved);
            remaining -= moved.getAmount();
        }
        if (remaining == source.getAmount()) return false;
        if (remaining <= 0) {
            sourceInventory.setItem(sourceSlot, null);
        } else {
            ItemStack left = source.clone();
            left.setAmount(remaining);
            sourceInventory.setItem(sourceSlot, left);
        }
        return true;
    }

    private static ItemStack incomingTopItem(InventoryClickEvent event, Player player) {
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> event.getCursor();
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int button = event.getHotbarButton();
                if (button >= 0 && button <= 8) yield player.getInventory().getItem(button);
                yield event.getClick() == ClickType.SWAP_OFFHAND
                        ? player.getInventory().getItemInOffHand() : null;
            }
            default -> null;
        };
    }

    private static boolean accepts(GuiInventory inventory, ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        GuiEditableSlots.Validation validation = inventory.getEditableSlots()
                .validate(inventory, item);
        if (validation.accepted()) return true;
        Component message = validation.rejectionMessage();
        if (message != null && !Component.empty().equals(message)) {
            inventory.getPlayer().sendMessage(message);
        }
        return false;
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

        GuiInventory current = inventories.get(uuid);
        GuiInventory inv = e.getInventory().getHolder() instanceof GuiInventory holder ? holder : current;
        if (inv == null || !inv.getBukkitInventory().equals(e.getInventory())) return;
        if (current != inv) {
            if (current != null) inv.getProvider().onClose(e, inv);
            return;
        }

        GuiProvider provider = inv.getProvider();

        if (!provider.allowClose(inv)) {
            Bukkit.getScheduler().runTask(plugin, inv::open);
            return;
        }

        inventories.remove(uuid, inv);
        provider.onClose(e, inv);
    }
}
