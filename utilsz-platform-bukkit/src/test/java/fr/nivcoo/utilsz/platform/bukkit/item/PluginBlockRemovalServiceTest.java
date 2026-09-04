package fr.nivcoo.utilsz.platform.bukkit.item;

import fr.nivcoo.utilsz.platform.bukkit.tracking.BlockChangeService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginBlockRemovalServiceTest {

    @Test
    void removesInTransactionThenTrackingThenCleanupThenDeliveryOrder() {
        BlockChangeService changes = mock(BlockChangeService.class);
        PluginBlockRemovalService removals = new PluginBlockRemovalService(changes);
        AtomicReference<Material> type = new AtomicReference<>(Material.CHEST);
        Block block = mutableBlock(type, true);
        List<String> order = new ArrayList<>();
        doAnswer(invocation -> {
            order.add("tracking");
            return null;
        }).when(changes).recordChange(block, Material.CHEST);

        boolean removed = removals.remove(block, material -> material == Material.CHEST,
                () -> {
                    assertEquals(Material.AIR, type.get());
                    order.add("commit");
                    return true;
                },
                () -> order.add("clear"),
                () -> order.add("delivery"));

        assertTrue(removed);
        assertEquals(Material.AIR, type.get());
        assertEquals(List.of("commit", "tracking", "clear", "delivery"), order);
    }

    @Test
    void restoresPhysicalBlockWhenPersistenceRejectsRemoval() {
        BlockChangeService changes = mock(BlockChangeService.class);
        PluginBlockRemovalService removals = new PluginBlockRemovalService(changes);
        AtomicReference<Material> type = new AtomicReference<>(Material.CHEST);
        Block block = mutableBlock(type, true);
        AtomicInteger sideEffects = new AtomicInteger();

        boolean removed = removals.remove(block, material -> material == Material.CHEST,
                () -> false, sideEffects::incrementAndGet, sideEffects::incrementAndGet);

        assertFalse(removed);
        assertEquals(Material.CHEST, type.get());
        assertEquals(0, sideEffects.get());
        verify(block).setType(Material.AIR, false);
        verify(block.getState()).update(true, false);
        verify(changes, never()).recordChange(any(Block.class), any(Material.class));
    }

    @Test
    void rejectsAStalePhysicalBlockBeforePersistence() {
        BlockChangeService changes = mock(BlockChangeService.class);
        PluginBlockRemovalService removals = new PluginBlockRemovalService(changes);
        AtomicReference<Material> type = new AtomicReference<>(Material.BARREL);
        Block block = mutableBlock(type, true);
        AtomicInteger commits = new AtomicInteger();

        boolean removed = removals.remove(block, material -> material == Material.CHEST,
                () -> {
                    commits.incrementAndGet();
                    return true;
                }, () -> { }, () -> { });

        assertFalse(removed);
        assertEquals(0, commits.get());
        assertEquals(Material.BARREL, type.get());
        verify(block, never()).setType(any(Material.class), any(Boolean.class));
        verify(changes, never()).recordChange(any(Block.class), any(Material.class));
    }

    @Test
    void doesNotDeliverOrTrackWhenPhysicalRemovalFails() {
        BlockChangeService changes = mock(BlockChangeService.class);
        PluginBlockRemovalService removals = new PluginBlockRemovalService(changes);
        AtomicReference<Material> type = new AtomicReference<>(Material.CHEST);
        Block block = mutableBlock(type, false);
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger cleared = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();

        boolean removed = removals.remove(block, material -> material == Material.CHEST,
                () -> {
                    commits.incrementAndGet();
                    return true;
                }, cleared::incrementAndGet, delivered::incrementAndGet);

        assertFalse(removed);
        assertEquals(0, commits.get());
        assertEquals(0, cleared.get());
        assertEquals(0, delivered.get());
        verify(changes, never()).recordChange(any(Block.class), any(Material.class));
    }

    @Test
    void restoresPhysicalBlockWhenPersistenceThrows() {
        BlockChangeService changes = mock(BlockChangeService.class);
        PluginBlockRemovalService removals = new PluginBlockRemovalService(changes);
        AtomicReference<Material> type = new AtomicReference<>(Material.CHEST);
        Block block = mutableBlock(type, true);
        IllegalStateException failure = new IllegalStateException("storage unavailable");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> removals.remove(
                block, material -> material == Material.CHEST,
                () -> {
                    throw failure;
                }, () -> { }, () -> { }));

        assertEquals(failure, thrown);
        assertEquals(Material.CHEST, type.get());
        verify(block.getState()).update(true, false);
        verify(changes, never()).recordChange(any(Block.class), any(Material.class));
    }

    @Test
    void tracksConfirmedRemovalButNeverDeliversWhenCleanupThrows() {
        BlockChangeService changes = mock(BlockChangeService.class);
        PluginBlockRemovalService removals = new PluginBlockRemovalService(changes);
        AtomicReference<Material> type = new AtomicReference<>(Material.CHEST);
        Block block = mutableBlock(type, true);
        AtomicInteger deliveries = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> removals.remove(
                block, material -> material == Material.CHEST, () -> true,
                () -> {
                    throw new IllegalStateException("cleanup failed");
                }, deliveries::incrementAndGet));

        assertEquals(Material.AIR, type.get());
        assertEquals(0, deliveries.get());
        verify(changes).recordChange(block, Material.CHEST);
    }

    @Test
    void repeatedRemovalCannotCommitOrDeliverTwice() {
        BlockChangeService changes = mock(BlockChangeService.class);
        PluginBlockRemovalService removals = new PluginBlockRemovalService(changes);
        AtomicReference<Material> type = new AtomicReference<>(Material.CHEST);
        Block block = mutableBlock(type, true);
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger deliveries = new AtomicInteger();

        assertTrue(removals.remove(block, material -> material == Material.CHEST,
                () -> {
                    commits.incrementAndGet();
                    return true;
                }, () -> { }, deliveries::incrementAndGet));
        assertFalse(removals.remove(block, material -> material == Material.CHEST,
                () -> {
                    commits.incrementAndGet();
                    return true;
                }, () -> { }, deliveries::incrementAndGet));

        assertEquals(1, commits.get());
        assertEquals(1, deliveries.get());
    }

    private static Block mutableBlock(AtomicReference<Material> type, boolean mutationSucceeds) {
        Block block = mock(Block.class);
        Material initialType = type.get();
        BlockState state = mock(BlockState.class);
        when(block.getType()).thenAnswer(invocation -> type.get());
        when(block.getState()).thenReturn(state);
        when(state.update(true, false)).thenAnswer(invocation -> {
            type.set(initialType);
            return true;
        });
        doAnswer(invocation -> {
            if (mutationSucceeds) type.set(invocation.getArgument(0));
            return null;
        }).when(block).setType(any(Material.class), any(Boolean.class));
        return block;
    }
}
