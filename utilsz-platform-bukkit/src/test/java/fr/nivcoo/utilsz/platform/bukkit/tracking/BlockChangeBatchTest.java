package fr.nivcoo.utilsz.platform.bukkit.tracking;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockChangeBatchTest {

    @Test
    void aggregatesChangesByChunkAndFlushesOnce() {
        BlockChangeService service = new BlockChangeService(mock(JavaPlugin.class));
        List<BlockChangeService.Changes> applied = new ArrayList<>();
        service.register(new BlockChangeProvider() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public void apply(BlockChangeService.Changes changes) {
                applied.add(changes);
            }
        });
        World world = mock(World.class);
        BlockChangeBatch batch = service.newBatch();

        batch.recordChange(block(world, 1, 70, 1, Material.AIR), Material.OAK_LOG);
        batch.recordChange(block(world, 2, 71, 2, Material.AIR), Material.OAK_LOG);
        batch.recordChange(block(world, 3, 72, 3, Material.OAK_SAPLING), Material.AIR);
        batch.recordChange(block(world, 17, 70, 1, Material.AIR), Material.BIRCH_LOG);

        batch.flush();

        assertEquals(2, applied.size());
        assertEquals(2, applied.getFirst().broken().get(Material.OAK_LOG));
        assertEquals(1, applied.getFirst().placed().get(Material.OAK_SAPLING));
        assertEquals(1, applied.getLast().broken().get(Material.BIRCH_LOG));
        assertTrue(batch.isEmpty());

        batch.flush();
        assertEquals(2, applied.size());
    }

    @Test
    void ignoresChangesUntilAProviderIsRegistered() {
        BlockChangeService service = new BlockChangeService(mock(JavaPlugin.class));
        BlockChangeBatch batch = service.newBatch();
        World world = mock(World.class);

        batch.recordChange(block(world, 1, 70, 1, Material.AIR), Material.OAK_LOG);

        assertTrue(batch.isEmpty());
    }

    private static Block block(World world, int x, int y, int z, Material currentType) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(currentType);
        return block;
    }

}
