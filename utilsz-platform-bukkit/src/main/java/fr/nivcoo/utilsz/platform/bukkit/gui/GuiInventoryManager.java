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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class GuiInventoryManager implements Listener {

    private final JavaPlugin plugin;
    private final HashMap<UUID, GuiInventory> inventories;
    private final Set<GuiInventory> pendingEditableChanges;
    private final Set<GuiInventory> pendingCloses;
    private boolean initialized;
    private int updateTaskId = -1;

    public GuiInventoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.inventories = new HashMap<>();
        this.pendingEditableChanges = Collections.newSetFromMap(new IdentityHashMap<>());
        this.pendingCloses = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (inventories.isEmpty()) return;

            var iterator = inventories.values().iterator();
            while (iterator.hasNext()) {
                GuiInventory inv = iterator.next();
                if (!isViewing(inv.getPlayer(), inv)) continue;

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
        return open(prepare(provider, p, params));
    }

    public GuiInventory prepare(GuiProvider provider, Player player) {
        return prepare(provider, player, null);
    }

    public GuiInventory prepare(GuiProvider provider, Player player, Consumer<GuiInventory> params) {
        return prepare(provider, player, null, params);
    }

    public GuiInventory prepare(
            GuiProvider provider,
            Player player,
            Inventory sharedInventory,
            Consumer<GuiInventory> params
    ) {
        if (provider == null) throw new IllegalArgumentException("provider cannot be null");
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        GuiInventory inv = new GuiInventory(player, provider, sharedInventory, params);
        provider.init(inv);
        return inv;
    }

    public GuiInventory open(GuiInventory inv) {
        if (inv == null) throw new IllegalArgumentException("inventory cannot be null");
        Player p = inv.getPlayer();
        UUID uuid = p.getUniqueId();
        GuiInventory previous = inventories.put(uuid, inv);
        try {
            inv.open();
            if (!isViewing(p, inv)) {
                throw new IllegalStateException("Managed inventory opening was cancelled");
            }
        } catch (RuntimeException exception) {
            if (previous == null || previous == inv) inventories.remove(uuid, inv);
            else inventories.replace(uuid, inv, previous);
            throw exception;
        }
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
        UUID uuid = p.getUniqueId();
        GuiInventory inventory = inventories.get(uuid);
        if (inventory != null && !isViewing(p, inventory)) inventories.remove(uuid, inventory);
        p.closeInventory();
    }

    public void closeAll() {
        for (GuiInventory inv : List.copyOf(inventories.values())) close(inv.getPlayer());
    }

    public void shutdown() {
        if (updateTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
        closeAll();
        inventories.clear();
        pendingEditableChanges.clear();
        pendingCloses.clear();
        HandlerList.unregisterAll(this);
        initialized = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        UUID uuid = e.getWhoClicked().getUniqueId();
        Player p = (Player) e.getWhoClicked();
        GuiInventory inv = get(p);
        Inventory top = e.getView().getTopInventory();
        if (inv == null) {
            if (isManagedInventory(top)) e.setCancelled(true);
            return;
        }
        if (!e.getView().getTopInventory().equals(inv.getBukkitInventory())) {
            if (isManagedInventory(top)) e.setCancelled(true);
            if (!isViewing(p, inv)) inventories.remove(uuid);
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        boolean isTop = e.getRawSlot() < topSize;

        GuiProvider provider = inv.getProvider();
        if (blockedAction(e.getAction())) {
            e.setCancelled(true);
            return;
        }
        if (isTop) {
            if (!editableTopSlot(inv.getEditableSlots().slots(),
                    inv.isManagedSlot(e.getSlot()), e.getSlot())) {
                e.setCancelled(true);
            } else if (!e.isCancelled() && !accepts(inv, e.getSlot(), incomingTopItem(e, p))) {
                e.setCancelled(true);
            } else if (!e.isCancelled()) {
                scheduleEditableChange(inv);
            }
        } else {
            provider.onBottomClick(e, inv);
            if (e.isCancelled()) return;
            if (provider.cancelBottomClicks(inv)) {
                e.setCancelled(true);
            } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (!e.isCancelled() && moveToEditableTop(inv, e.getClickedInventory(), e.getSlot())) {
                    scheduleEditableChange(inv);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        UUID uuid = e.getWhoClicked().getUniqueId();
        Player p = (Player) e.getWhoClicked();
        GuiInventory inv = get(p);
        Inventory top = e.getView().getTopInventory();
        if (inv == null) {
            if (isManagedInventory(top)) e.setCancelled(true);
            return;
        }
        if (!e.getView().getTopInventory().equals(inv.getBukkitInventory())) {
            if (isManagedInventory(top)) e.setCancelled(true);
            if (!isViewing(p, inv)) inventories.remove(uuid);
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        if (inv.getProvider().cancelBottomClicks(inv) && touchesBottom(e.getRawSlots(), topSize)) {
            e.setCancelled(true);
            return;
        }
        boolean touchesLockedTop = e.getRawSlots().stream()
                .filter(raw -> raw < topSize)
                .anyMatch(raw -> !inv.getEditableSlots().dragAllowed(raw)
                        || !editableTopSlot(inv.getEditableSlots().slots(),
                        inv.isManagedSlot(raw), raw));
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        boolean rejected = !e.isCancelled() && e.getRawSlots().stream()
                .filter(raw -> raw < topSize)
                .anyMatch(raw -> !accepts(inv, raw, e.getOldCursor()));
        if (touchesLockedTop || rejected) {
            e.setCancelled(true);
        } else if (touchesTop && !e.isCancelled()) {
            scheduleEditableChange(inv);
        }
    }

    static boolean editableTopSlot(
            Collection<Integer> editableSlots,
            boolean managed,
            int slot
    ) {
        return !managed && editableSlots != null && editableSlots.contains(slot);
    }

    static boolean touchesBottom(Collection<Integer> rawSlots, int topSize) {
        return rawSlots != null && rawSlots.stream().anyMatch(slot -> slot != null && slot >= topSize);
    }

    static boolean blockedAction(InventoryAction action) {
        return action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.CLONE_STACK
                || action == InventoryAction.UNKNOWN;
    }

    private static boolean moveToEditableTop(GuiInventory inv, Inventory sourceInventory, int sourceSlot) {
        if (sourceInventory == null || sourceSlot < 0 || sourceSlot >= sourceInventory.getSize()) return false;
        Collection<Integer> editableSlots = inv.getEditableSlots().slots();
        if (editableSlots == null || editableSlots.isEmpty()) return false;
        ItemStack source = sourceInventory.getItem(sourceSlot);
        if (source == null || source.getType().isAir() || source.getAmount() <= 0) return false;
        GuiEditableSlots.Validation rejection = null;
        List<Integer> acceptedSlots = new java.util.ArrayList<>();
        for (Integer slot : editableSlots) {
            if (slot == null || !inv.getEditableSlots().shiftClickAllowed(slot)) continue;
            GuiEditableSlots.Validation validation = inv.getEditableSlots().validate(inv, slot, source);
            if (validation.accepted()) acceptedSlots.add(slot);
            else if (rejection == null && validation.rejectionMessage() != null) rejection = validation;
        }
        if (acceptedSlots.isEmpty()) {
            if (rejection != null) inv.getPlayer().sendMessage(rejection.rejectionMessage());
            return false;
        }
        Inventory top = inv.getBukkitInventory();
        int remaining = source.getAmount();
        for (Integer slot : acceptedSlots) {
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
        for (Integer slot : acceptedSlots) {
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
            case HOTBAR_SWAP -> {
                int button = event.getHotbarButton();
                if (button >= 0 && button <= 8) yield player.getInventory().getItem(button);
                yield event.getClick() == ClickType.SWAP_OFFHAND
                        ? player.getInventory().getItemInOffHand() : null;
            }
            default -> null;
        };
    }

    private static boolean accepts(GuiInventory inventory, int slot, ItemStack item) {
        GuiEditableSlots.Validation validation = inventory.getEditableSlots()
                .validate(inventory, slot, item);
        if (validation.accepted()) return true;
        Component message = validation.rejectionMessage();
        if (message != null && !Component.empty().equals(message)) {
            inventory.getPlayer().sendMessage(message);
        }
        return false;
    }

    private void scheduleEditableChange(GuiInventory inventory) {
        if (!pendingEditableChanges.add(inventory)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingEditableChanges.remove(inventory);
            if (inventories.get(inventory.getPlayer().getUniqueId()) == inventory) {
                inventory.getProvider().onEditableChange(inventory);
            }
        });
    }

    private boolean isViewing(Player player, GuiInventory inv) {
        return player != null
                && player.isOnline()
                && inv != null
                && player.getOpenInventory().getTopInventory().equals(inv.getBukkitInventory());
    }

    private boolean isManagedInventory(Inventory inventory) {
        if (inventory == null) return false;
        if (inventory.getHolder() instanceof GuiInventory) return true;
        if (inventory.getHolder() instanceof ManagedGuiInventoryHolder holder && holder.isManagedGuiInventory()) return true;
        return inventories.values().stream().anyMatch(inv -> inventory.equals(inv.getBukkitInventory()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GuiInventory inventory = inventories.get(player.getUniqueId());
        if (inventory == null || !inventory.getBukkitInventory().equals(event.getInventory())) return;
        Component title = inventory.titleForOpen();
        event.titleOverride(title);
        inventory.displayedTitle(title);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        GuiInventory current = inventories.get(uuid);
        GuiInventory inv = e.getInventory().getHolder() instanceof GuiInventory holder ? holder : current;
        if (inv == null || !inv.getBukkitInventory().equals(e.getInventory())) return;
        if (current != inv) {
            if (current != null) inv.getProvider().onClose(e, inv);
            return;
        }

        GuiProvider provider = inv.getProvider();

        if (!pendingCloses.add(inv)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingCloses.remove(inv);
            if (inventories.get(uuid) != inv || isViewing(inv.getPlayer(), inv)) return;
            if (!provider.allowClose(inv)) {
                try {
                    open(inv);
                } catch (RuntimeException exception) {
                    inventories.remove(uuid, inv);
                    provider.onClose(e, inv);
                    plugin.getLogger().warning("Unable to reopen managed inventory: "
                            + exception.getMessage());
                }
                return;
            }
            inventories.remove(uuid, inv);
            provider.onClose(e, inv);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        GuiInventory inventory = inventories.remove(uuid);
        if (inventory != null) pendingCloses.remove(inventory);
        if (inventory != null) inventory.getProvider().onQuit(inventory);
    }
}
