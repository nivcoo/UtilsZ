package fr.nivcoo.utilsz.platform.bukkit.item;

import fr.nivcoo.utilsz.platform.bukkit.tracking.BlockChangeService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Applies the destructive part of a managed block removal in one fixed order.
 * Plugin-specific persistence, inventory cleanup and delivery stay in callbacks.
 */
public final class PluginBlockRemovalService {

    private final BlockChangeService blockChanges;

    public PluginBlockRemovalService(BlockChangeService blockChanges) {
        this.blockChanges = Objects.requireNonNull(blockChanges, "blockChanges");
    }

    public boolean remove(Block block, Predicate<Material> expectedMaterial,
                          BooleanSupplier commitStateRemoval, Runnable clearContents,
                          Runnable deliverDrops) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(expectedMaterial, "expectedMaterial");
        Objects.requireNonNull(commitStateRemoval, "commitStateRemoval");
        Objects.requireNonNull(clearContents, "clearContents");
        Objects.requireNonNull(deliverDrops, "deliverDrops");

        Material previousType = block.getType();
        if (BlockChangeService.isAir(previousType) || !expectedMaterial.test(previousType)) {
            return false;
        }

        BlockState previousState = block.getState();
        try {
            block.setType(Material.AIR, false);
        } catch (RuntimeException | Error failure) {
            restore(previousState, block, previousType, failure);
            throw failure;
        }
        if (!BlockChangeService.isAir(block.getType())) {
            restore(previousState, block, previousType, null);
            return false;
        }
        boolean committed;
        try {
            committed = commitStateRemoval.getAsBoolean();
        } catch (RuntimeException | Error failure) {
            restore(previousState, block, previousType, failure);
            throw failure;
        }
        if (!committed) {
            restore(previousState, block, previousType, null);
            return false;
        }

        blockChanges.recordChange(block, previousType);
        clearContents.run();
        deliverDrops.run();
        return true;
    }

    private void restore(BlockState previousState, Block block, Material previousType, Throwable cause) {
        if (block.getType() == previousType) return;
        try {
            boolean restored = previousState.update(true, false);
            if (restored && block.getType() == previousType) return;
            IllegalStateException failure = new IllegalStateException(
                    "Unable to restore managed block after removal rollback");
            if (cause == null) throw failure;
            cause.addSuppressed(failure);
        } catch (RuntimeException | Error rollbackFailure) {
            if (cause == null) throw rollbackFailure;
            cause.addSuppressed(rollbackFailure);
        }
    }
}
