package fr.nivcoo.utilsz.platform.bukkit.session;

import io.papermc.paper.event.entity.EntityBreakByEntityEvent;
import io.papermc.paper.event.entity.EntityBreakEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerSessionManagerTest {

    private JavaPlugin plugin;
    private PluginManager plugins;
    private PlayerSessionManager manager;
    private Player player;

    @BeforeEach
    void prepare() {
        plugin = mock(JavaPlugin.class);
        plugins = mock(PluginManager.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        manager = new PlayerSessionManager(plugin);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            manager.init();
            manager.init();
        }
    }

    @Test
    void cancelsDestructionBeforeCompletedSelectionConsumesTheSession() throws Exception {
        for (boolean cancelled : List.of(false, true)) {
            for (Event early : earlyEvents(player)) {
                AtomicInteger selected = new AtomicInteger();
                TargetSession<String> session = session(true, selected, new AtomicInteger());
                manager.start(player, session);
                ((Cancellable) early).setCancelled(cancelled);

                dispatch(early);
                boolean destroyed = !((Cancellable) early).isCancelled();

                assertFalse(destroyed, early.getEventName());
                assertSame(session, manager.get(player));
                assertEquals(0, selected.get());
                PrePlayerAttackEntityEvent attack = new PrePlayerAttackEntityEvent(player, target(early), false);
                dispatch(attack);
                assertTrue(attack.isCancelled());
                assertEquals(1, selected.get());
                assertNull(manager.get(player));
            }
        }
    }

    @Test
    void successiveBreakEventsDoNotSelectOrReportAnInvalidTargetTwice() throws Exception {
        AtomicInteger selected = new AtomicInteger();
        AtomicInteger invalid = new AtomicInteger();
        TargetSession<String> session = session(false, selected, invalid);
        manager.start(player, session);
        Hanging hanging = mock(Hanging.class);
        HangingBreakByEntityEvent first = new HangingBreakByEntityEvent(hanging, player, mock(DamageSource.class));

        dispatch(first);
        assertTrue(first.isCancelled());
        EntityBreakByEntityEvent second = breakByEntity(hanging, player);
        second.setCancelled(first.isCancelled());
        dispatch(second);
        assertTrue(second.isCancelled());
        assertEquals(0, selected.get());
        assertEquals(0, invalid.get());
        PrePlayerAttackEntityEvent attack = new PrePlayerAttackEntityEvent(player, hanging, false);
        dispatch(attack);

        assertTrue(attack.isCancelled());
        assertEquals(1, selected.get());
        assertEquals(1, invalid.get());
        assertSame(session, manager.get(player));
    }

    @Test
    void alreadyBlockedDirectAttacksStillSelectOnce() throws Exception {
        for (boolean willAttack : List.of(true, false)) {
            AtomicInteger selected = new AtomicInteger();
            manager.start(player, session(true, selected, new AtomicInteger()));
            PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, mock(Entity.class), willAttack);
            event.setCancelled(true);

            dispatch(event);

            assertTrue(event.isCancelled());
            assertEquals(1, selected.get());
            assertNull(manager.get(player));
        }
    }

    @Test
    void guardsLeavePlayersWithoutTargetSessionsUnaffected() throws Exception {
        for (boolean chatting : List.of(false, true)) {
            ChatInputSession<String> session = new ChatInputSession<>("chat", false, null, null, null);
            if (chatting) manager.start(player, session);
            List<Event> events = earlyEvents(player);
            events.add(new PrePlayerAttackEntityEvent(player, mock(Entity.class), true));
            for (Event event : events) {
                dispatch(event);
                assertFalse(((Cancellable) event).isCancelled(), event.getEventName());
            }
            assertSame(chatting ? session : null, manager.get(player));
        }
        manager.start(player, session(false, new AtomicInteger(), new AtomicInteger()));
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        for (Event event : earlyEvents(other)) {
            dispatch(event);
            assertFalse(((Cancellable) event).isCancelled(), event.getEventName());
        }
    }

    @Test
    void listenerRegistersOnceAndIgnoresUnrelatedRemoval() throws Exception {
        verify(plugins, times(1)).registerEvents(manager, plugin);
        verifyNoMoreInteractions(plugins);
        manager.start(player, session(true, new AtomicInteger(), new AtomicInteger()));
        EntityBreakEvent physics = new EntityBreakEvent(mock(Entity.class), EntityBreakEvent.RemoveCause.PHYSICS);

        dispatch(physics);
        EntityBreakByEntityEvent nonPlayer = breakByEntity(mock(Entity.class), mock(Entity.class));
        dispatch(nonPlayer);

        assertFalse(physics.isCancelled());
        assertFalse(nonPlayer.isCancelled());
        assertNotNull(manager.get(player));
    }

    private TargetSession<String> session(boolean complete, AtomicInteger selected, AtomicInteger invalid) {
        return new TargetSession<>("target", null, 0, null, context -> {
            selected.incrementAndGet();
            return complete;
        }, context -> invalid.incrementAndGet(), null, null, null);
    }

    private List<Event> earlyEvents(Player attacker) {
        List<Event> events = new ArrayList<>();
        events.add(new EntityDamageByEntityEvent(attacker, mock(Entity.class),
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 1));
        events.add(new HangingBreakByEntityEvent(mock(Hanging.class), attacker, mock(DamageSource.class),
                HangingBreakEvent.RemoveCause.ENTITY));
        events.add(breakByEntity(mock(Entity.class), attacker));
        return events;
    }

    private EntityBreakByEntityEvent breakByEntity(Entity target, Entity attacker) {
        return new EntityBreakByEntityEvent(target, attacker, mock(DamageSource.class),
                EntityBreakEvent.RemoveCause.ENTITY);
    }

    private Entity target(Event event) {
        if (event instanceof HangingBreakByEntityEvent hanging) return hanging.getEntity();
        return ((EntityEvent) event).getEntity();
    }

    private void dispatch(Event event) throws Exception {
        for (Method method : PlayerSessionManager.class.getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null || !method.getParameterTypes()[0].isInstance(event)) continue;
            if (handler.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled())
                continue;
            method.invoke(manager, event);
        }
    }
}
