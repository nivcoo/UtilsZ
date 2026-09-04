package fr.nivcoo.utilsz.platform.bukkit.tracking;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockChangeServiceTest {

    @Test
    void retriesAProviderAfterItsFailureCooldown() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("BlockChangeServiceTest"));
        AtomicLong clock = new AtomicLong();
        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean failing = new AtomicBoolean(true);
        BlockChangeService service = new BlockChangeService(plugin, clock::get);
        service.register(new BlockChangeProvider() {
            @Override
            public String id() {
                return "recoverable";
            }

            @Override
            public void apply(BlockChangeService.Changes changes) {
                attempts.incrementAndGet();
                if (failing.get()) throw new IllegalStateException("temporary");
            }
        });
        Location location = new Location(mock(World.class), 0, 64, 0);

        service.recordChanges(location, Map.of(Material.STONE, 1), Map.of());
        service.recordChanges(location, Map.of(Material.STONE, 1), Map.of());
        assertEquals(1, attempts.get());

        failing.set(false);
        clock.addAndGet(BlockChangeService.PROVIDER_RETRY_DELAY_MILLIS);
        service.recordChanges(location, Map.of(Material.STONE, 1), Map.of());
        service.recordChanges(location, Map.of(Material.STONE, 1), Map.of());

        assertEquals(3, attempts.get());
    }
}
