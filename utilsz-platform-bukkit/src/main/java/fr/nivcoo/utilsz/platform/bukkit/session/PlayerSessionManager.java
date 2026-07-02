package fr.nivcoo.utilsz.platform.bukkit.session;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class PlayerSessionManager implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerSession<?>> sessions = new ConcurrentHashMap<>();
    private boolean initialized;

    public PlayerSessionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (initialized) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        initialized = true;
    }

    public void start(Player player, PlayerSession<?> session) {
        if (player == null || session == null) return;
        sessions.put(player.getUniqueId(), session);
    }

    public PlayerSession<?> get(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public PlayerSession<?> get(UUID playerUuid) {
        return playerUuid == null ? null : sessions.get(playerUuid);
    }

    public PlayerSession<?> cancel(Player player) {
        if (player == null) return null;
        return sessions.remove(player.getUniqueId());
    }

    public void clear() {
        sessions.clear();
    }

    public void removeIf(BiPredicate<UUID, PlayerSession<?>> predicate) {
        if (predicate == null) return;
        sessions.entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        TargetSession<?> session = targetSession(event.getPlayer());
        if (session == null) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
            cancelTarget(event.getPlayer(), session, session.onCancel());
            return;
        }
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        handleBlock(event.getPlayer(), session, event.getClickedBlock(), action);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onArmSwing(PlayerArmSwingEvent event) {
        Player player = event.getPlayer();
        TargetSession<?> session = targetSession(player);
        if (session == null || event.getHand() == EquipmentSlot.OFF_HAND) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        TargetSession<?> session = targetSession(event.getPlayer());
        if (session == null) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        handleEntity(event.getPlayer(), session, event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        TargetSession<?> session = targetSession(player);
        if (session == null) return;
        event.setCancelled(true);
        handleEntity(player, session, event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        TargetSession<?> session = targetSession(event.getPlayer());
        if (session == null) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        PlayerSession<?> current = sessions.get(event.getPlayer().getUniqueId());
        if (!(current instanceof ChatInputSession<?> session)) return;
        event.setCancelled(true);
        String message = PLAIN.serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleChat(event.getPlayer(), session, message));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        PlayerSession<?> current = sessions.get(event.getPlayer().getUniqueId());
        if (!(current instanceof ChatInputSession<?> session)) return;
        if (!event.getMessage().equalsIgnoreCase("/cancel")) return;
        event.setCancelled(true);
        if (sessions.remove(event.getPlayer().getUniqueId(), session)) {
            callChatCancel(event.getPlayer(), session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!changedBlockOrWorld(event.getFrom(), event.getTo())) return;
        checkBounds(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        checkBounds(event.getPlayer(), event.getTo());
    }

    private TargetSession<?> targetSession(Player player) {
        PlayerSession<?> session = sessions.get(player.getUniqueId());
        return session instanceof TargetSession<?> targetSession ? targetSession : null;
    }

    private <T> void handleBlock(Player player, TargetSession<T> session, Block block, Action action) {
        if (block == null) {
            session.onInvalidTarget().accept(new SessionContext<>(this, player, session));
            return;
        }
        boolean complete = Boolean.TRUE.equals(session.onBlockClick().apply(new TargetBlockContext<>(this, player, session, block, action)));
        if (complete) sessions.remove(player.getUniqueId(), session);
    }

    private <T> void handleEntity(Player player, TargetSession<T> session, Entity entity) {
        boolean complete = Boolean.TRUE.equals(session.onEntityClick().apply(new TargetEntityContext<>(this, player, session, entity)));
        if (complete) sessions.remove(player.getUniqueId(), session);
        else session.onInvalidTarget().accept(new SessionContext<>(this, player, session));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void cancelTarget(Player player, TargetSession<?> session, Consumer<?> callback) {
        if (!sessions.remove(player.getUniqueId(), session)) return;
        ((Consumer) callback).accept(new SessionContext(this, player, session));
    }

    private <T> void handleChat(Player player, ChatInputSession<T> session, String message) {
        if (sessions.get(player.getUniqueId()) != session) return;
        if (!session.allowEmpty() && (message == null || message.isBlank())) {
            session.onInvalidInput().accept(new ChatInputContext<>(this, player, session, message == null ? "" : message));
            return;
        }
        sessions.remove(player.getUniqueId(), session);
        session.onInput().accept(new ChatInputContext<>(this, player, session, message == null ? "" : message));
    }

    private <T> void callChatCancel(Player player, ChatInputSession<T> session) {
        session.onCancel().accept(new SessionContext<>(this, player, session));
    }

    private void checkBounds(Player player, Location to) {
        if (to == null) return;
        TargetSession<?> session = targetSession(player);
        if (session == null || session.origin() == null || session.origin().getWorld() == null || to.getWorld() == null) return;
        if (!session.origin().getWorld().equals(to.getWorld())) {
            cancelTarget(player, session, session.onWorldChanged());
            return;
        }
        double max = session.maxDistance();
        if (max > 0 && session.origin().distanceSquared(to) > max * max) {
            cancelTarget(player, session, session.onTooFar());
        }
    }

    private boolean changedBlockOrWorld(Location from, Location to) {
        if (to == null) return false;
        if (!from.getWorld().equals(to.getWorld())) return true;
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
