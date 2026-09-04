package fr.nivcoo.utilsz.platform.bukkit.tracking;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlockChangeBatch {

    private final BlockChangeService service;
    private final Map<ChunkKey, PendingChanges> pendingByChunk = new LinkedHashMap<>();

    BlockChangeBatch(BlockChangeService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public void recordChange(Block block, Material previousType) {
        if (block == null) return;
        recordChange(block, previousType, block.getType());
    }

    public void recordChange(Block block, Material previousType, Material currentType) {
        if (block == null || previousType == currentType || !service.hasProviders()) return;
        boolean hasBroken = !BlockChangeService.isAir(previousType);
        boolean hasPlaced = !BlockChangeService.isAir(currentType);
        if (!hasBroken && !hasPlaced) return;

        PendingChanges pending = pending(block);
        if (pending == null) return;
        if (hasBroken) pending.broken.merge(previousType, 1, Integer::sum);
        if (hasPlaced) pending.placed.merge(currentType, 1, Integer::sum);
    }

    public boolean isEmpty() {
        return pendingByChunk.isEmpty();
    }

    public void clear() {
        pendingByChunk.clear();
    }

    public void flush() {
        if (pendingByChunk.isEmpty()) return;
        List<PendingChanges> pendingChanges = new ArrayList<>(pendingByChunk.values());
        pendingByChunk.clear();
        for (PendingChanges pending : pendingChanges) {
            service.recordChanges(pending.location, pending.broken, pending.placed);
        }
    }

    private PendingChanges pending(Block block) {
        World world = block.getWorld();
        if (world == null) return null;
        ChunkKey key = new ChunkKey(world, block.getX() >> 4, block.getZ() >> 4);
        return pendingByChunk.computeIfAbsent(key, ignored -> new PendingChanges(
                new Location(world, block.getX(), block.getY(), block.getZ())
        ));
    }

    private record ChunkKey(World world, int x, int z) {
    }

    private static final class PendingChanges {
        private final Location location;
        private final Map<Material, Integer> broken = new EnumMap<>(Material.class);
        private final Map<Material, Integer> placed = new EnumMap<>(Material.class);

        private PendingChanges(Location location) {
            this.location = location;
        }
    }
}
