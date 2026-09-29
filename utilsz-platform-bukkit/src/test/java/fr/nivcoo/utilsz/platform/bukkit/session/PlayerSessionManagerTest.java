package fr.nivcoo.utilsz.platform.bukkit.session;

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
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.plugin.EventExecutor;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSessionManagerTest {

    private static final String BREAK_EVENT = "io.papermc.paper.event.entity.EntityBreakEvent";

    private final List<Registration> registrations = new ArrayList<>();
    private PlayerSessionManager manager;
    private Player player;

    @BeforeEach
    void prepare() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PluginManager plugins = mock(PluginManager.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        manager = new PlayerSessionManager(plugin);
        doAnswer(invocation -> {
            registrations.add(new Registration(invocation.getArgument(0), invocation.getArgument(3), invocation.getArgument(5)));
            return null;
        }).when(plugins).registerEvent(any(), same(manager), any(), any(), same(plugin), anyBoolean());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            manager.init();
            manager.init();
        }
    }

    @Test
    void cancelsDestructionBeforeCompletedSelectionConsumesTheSession() throws Exception {
        for (Event early : earlyEvents(player)) {
            AtomicInteger selected = new AtomicInteger();
            TargetSession<String> session = session(true, selected, new AtomicInteger());
            manager.start(player, session);

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
        if (hasBreakEvent()) {
            Event second = breakByEntity(hanging, player);
            ((Cancellable) second).setCancelled(first.isCancelled());
            dispatch(second);
            assertTrue(((Cancellable) second).isCancelled());
        }
        assertEquals(0, selected.get());
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
    void optionalListenerRegistersOnceWhenAvailableAndIgnoresUnrelatedRemoval() throws Exception {
        assertEquals(hasBreakEvent() ? 1 : 0, registrations.size());
        if (!hasBreakEvent()) return;
        assertEquals(BREAK_EVENT, registrations.getFirst().type().getName());
        manager.start(player, session(true, new AtomicInteger(), new AtomicInteger()));
        Class<?> causeClass = Class.forName(BREAK_EVENT + "$RemoveCause");
        Object cause = causeClass.getField("PHYSICS").get(null);
        Event physics = (Event) Class.forName(BREAK_EVENT)
                .getConstructor(Entity.class, causeClass).newInstance(mock(Entity.class), cause);

        dispatch(physics);
        Event nonPlayer = breakByEntity(mock(Entity.class), mock(Entity.class));
        dispatch(nonPlayer);

        assertFalse(((Cancellable) physics).isCancelled());
        assertFalse(((Cancellable) nonPlayer).isCancelled());
        assertNotNull(manager.get(player));
    }

    private TargetSession<String> session(boolean complete, AtomicInteger selected, AtomicInteger invalid) {
        return new TargetSession<>("target", null, 0, null, context -> {
            selected.incrementAndGet();
            return complete;
        }, context -> invalid.incrementAndGet(), null, null, null);
    }

    private List<Event> earlyEvents(Player attacker) throws Exception {
        List<Event> events = new ArrayList<>();
        events.add(new EntityDamageByEntityEvent(attacker, mock(Entity.class),
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 1));
        events.add(new HangingBreakByEntityEvent(mock(Hanging.class), attacker, mock(DamageSource.class),
                HangingBreakEvent.RemoveCause.ENTITY));
        if (hasBreakEvent()) events.add(breakByEntity(mock(Entity.class), attacker));
        return events;
    }

    private Event breakByEntity(Entity target, Entity attacker) throws Exception {
        Class<?> causeClass = Class.forName(BREAK_EVENT + "$RemoveCause");
        Object cause = causeClass.getField("ENTITY").get(null);
        return (Event) Class.forName("io.papermc.paper.event.entity.EntityBreakByEntityEvent")
                .getConstructor(Entity.class, Entity.class, DamageSource.class, causeClass)
                .newInstance(target, attacker, mock(DamageSource.class), cause);
    }

    private boolean hasBreakEvent() {
        try {
            Class.forName(BREAK_EVENT);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private Entity target(Event event) throws Exception {
        return (Entity) event.getClass().getMethod("getEntity").invoke(event);
    }

    private void dispatch(Event event) throws Exception {
        for (Method method : PlayerSessionManager.class.getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null || !method.getParameterTypes()[0].isInstance(event)) continue;
            if (handler.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled()) continue;
            method.invoke(manager, event);
        }
        for (Registration registration : registrations) {
            if (!registration.type().isInstance(event)) continue;
            if (registration.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled()) continue;
            registration.executor().execute(manager, event);
        }
    }

    private record Registration(Class<? extends Event> type, EventExecutor executor, boolean ignoreCancelled) {
    }
}