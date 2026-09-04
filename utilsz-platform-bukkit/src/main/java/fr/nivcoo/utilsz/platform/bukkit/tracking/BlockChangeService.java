package fr.nivcoo.utilsz.platform.bukkit.tracking;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.logging.Level;

public final class BlockChangeService {

    static final long PROVIDER_RETRY_DELAY_MILLIS = 5_000L;

    private final JavaPlugin plugin;
    private final LongSupplier clock;
    private final Map<String, ProviderState> providers = new LinkedHashMap<>();

    public BlockChangeService(JavaPlugin plugin) {
        this(plugin, System::currentTimeMillis);
    }

    BlockChangeService(JavaPlugin plugin, LongSupplier clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void register(BlockChangeProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) return;
        providers.put(provider.id(), new ProviderState(provider));
    }

    public BlockChangeBatch newBatch() {
        return new BlockChangeBatch(this);
    }

    public void recordChange(Block block, Material previousType) {
        if (block == null) return;
        recordChange(block.getLocation(), previousType, block.getType());
    }

    public void recordChange(Location location, Material previousType, Material currentType) {
        if (previousType == currentType) return;
        Map<Material, Integer> broken = isAir(previousType)
                ? Map.of()
                : Map.of(previousType, 1);
        Map<Material, Integer> placed = isAir(currentType)
                ? Map.of()
                : Map.of(currentType, 1);
        recordChanges(location, broken, placed);
    }

    public void recordChanges(Location location, Map<Material, Integer> broken,
                              Map<Material, Integer> placed) {
        if (location == null || location.getWorld() == null || providers.isEmpty()) return;
        Map<Material, Integer> normalizedBroken = normalize(broken);
        Map<Material, Integer> normalizedPlaced = normalize(placed);
        if (normalizedBroken.isEmpty() && normalizedPlaced.isEmpty()) return;

        Changes changes = new Changes(location.clone(), normalizedBroken, normalizedPlaced);
        long now = clock.getAsLong();
        for (ProviderState state : List.copyOf(providers.values())) {
            if (state.retryAfterMillis > now) continue;
            try {
                state.provider.apply(changes);
                state.retryAfterMillis = 0L;
            } catch (Throwable throwable) {
                state.retryAfterMillis = now + PROVIDER_RETRY_DELAY_MILLIS;
                plugin.getLogger().log(Level.WARNING,
                        "Block change provider " + state.provider.id()
                                + " suspended for 5 seconds: " + throwable.getMessage(),
                        throwable);
            }
        }
    }

    private Map<Material, Integer> normalize(Map<Material, Integer> counts) {
        if (counts == null || counts.isEmpty()) return Map.of();
        Map<Material, Integer> normalized = new EnumMap<>(Material.class);
        counts.forEach((material, amount) -> {
            if (isAir(material) || amount == null || amount <= 0) return;
            normalized.merge(material, amount, Integer::sum);
        });
        return Map.copyOf(normalized);
    }

    public static boolean isAir(Material material) {
        return material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }

    boolean hasProviders() {
        return !providers.isEmpty();
    }

    public record Changes(Location location, Map<Material, Integer> broken,
                          Map<Material, Integer> placed) {
    }

    private static final class ProviderState {
        private final BlockChangeProvider provider;
        private long retryAfterMillis;

        private ProviderState(BlockChangeProvider provider) {
            this.provider = provider;
        }
    }
}
