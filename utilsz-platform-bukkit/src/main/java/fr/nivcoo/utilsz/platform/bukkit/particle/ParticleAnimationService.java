package fr.nivcoo.utilsz.platform.bukkit.particle;

import fr.nivcoo.utilsz.platform.bukkit.particle.animation.ParticleAnimationRenderContext;
import fr.nivcoo.utilsz.platform.bukkit.particle.animation.ParticleAnimationRenderer;
import fr.nivcoo.utilsz.platform.bukkit.particle.animation.SmoothSpiralParticleAnimation;
import fr.nivcoo.utilsz.platform.bukkit.particle.animation.StaticParticleAnimation;
import fr.nivcoo.utilsz.platform.bukkit.particle.animation.TrailSpiralParticleAnimation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class ParticleAnimationService<T> {

    private final JavaPlugin plugin;
    private final Supplier<Collection<T>> source;
    private final Function<T, String> keyResolver;
    private final Function<T, Location> locationResolver;
    private final Function<T, ParticleAnimationConfig> configResolver;
    private final Queue<T> queue = new ArrayDeque<>();
    private final Map<ParticleAnimationType, ParticleAnimationRenderer> renderers = new EnumMap<>(ParticleAnimationType.class);
    private final ParticleSpawner spawner = new ParticleSpawner();
    private BukkitTask task;
    private long ticks;

    public ParticleAnimationService(JavaPlugin plugin, Supplier<Collection<T>> source, Function<T, String> keyResolver,
                                    Function<T, Location> locationResolver, Function<T, ParticleAnimationConfig> configResolver) {
        this.plugin = plugin;
        this.source = source;
        this.keyResolver = keyResolver;
        this.locationResolver = locationResolver;
        this.configResolver = configResolver;
        renderers.put(ParticleAnimationType.STATIC, new StaticParticleAnimation());
        renderers.put(ParticleAnimationType.SPIRAL, new SmoothSpiralParticleAnimation());
        renderers.put(ParticleAnimationType.TRAIL_SPIRAL, new TrailSpiralParticleAnimation());
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        queue.clear();
        renderers.values().forEach(ParticleAnimationRenderer::clear);
    }

    public void forget(T target) {
        if (target == null) return;
        String key = keyResolver.apply(target);
        renderers.values().forEach(renderer -> renderer.forget(key));
    }

    private void tick() {
        ticks++;
        Collection<T> targets = source.get();
        if (targets == null || targets.isEmpty()) return;
        if (queue.isEmpty()) queue.addAll(targets);
        int budget = Math.max(1, maxBudget(targets));
        while (budget-- > 0 && !queue.isEmpty()) {
            T target = queue.poll();
            if (target != null) render(target);
        }
    }

    private int maxBudget(Collection<T> targets) {
        int budget = 1;
        for (T target : targets) {
            ParticleAnimationConfig config = configResolver.apply(target);
            if (config != null) budget = Math.max(budget, config.itemsPerTick);
        }
        return budget;
    }

    private void render(T target) {
        ParticleAnimationConfig config = configResolver.apply(target);
        if (config == null || !config.enabled || ticks % Math.max(1, config.intervalTicks) != 0) return;
        Location block = locationResolver.apply(target);
        if (block == null || block.getWorld() == null || !block.isChunkLoaded()) return;
        World world = block.getWorld();
        String key = keyResolver.apply(target);
        ParticleAnimationRenderer renderer = renderers.getOrDefault(config.animation, renderers.get(ParticleAnimationType.STATIC));
        renderer.render(new ParticleAnimationRenderContext(key, world, block, config, spawner));
    }
}
