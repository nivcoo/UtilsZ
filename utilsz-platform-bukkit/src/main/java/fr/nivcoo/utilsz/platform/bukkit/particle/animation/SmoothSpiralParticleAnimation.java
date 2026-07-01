package fr.nivcoo.utilsz.platform.bukkit.particle.animation;

import fr.nivcoo.utilsz.platform.bukkit.particle.ParticleAnimationConfig;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.HashMap;
import java.util.Map;

public final class SmoothSpiralParticleAnimation implements ParticleAnimationRenderer {

    private final Map<String, Double> phases = new HashMap<>();

    @Override
    public void render(ParticleAnimationRenderContext context) {
        ParticleAnimationConfig.Spiral spiral = context.config().spiral;
        double phase = phases.getOrDefault(context.key(), 0.0);
        double centerX = context.block().getBlockX() + 0.5;
        double centerZ = context.block().getBlockZ() + 0.5;
        double baseY = context.block().getBlockY();
        double radius = Math.max(0.0, spiral.radius);
        double height = Math.max(0.05, spiral.height);
        double pointSpacing = Math.max(0.05, spiral.pointSpacing);
        int points = Math.clamp(spiral.trailLength * 2L, 4, 48);
        for (int index = 0; index < points; index++) {
            double angle = phase - index * pointSpacing;
            double yRatio = (Math.sin(angle * 0.5) + 1.0) * 0.5;
            Location point = new Location(context.world(),
                    centerX + radius * Math.cos(angle),
                    baseY + yRatio * height,
                    centerZ + radius * Math.sin(angle));
            context.spawner().spawn(context.world(), Particle.DUST, point, 1, 0, 0, 0, 0,
                    context.config().effect.color, spiral.dustSize);
        }
        phases.put(context.key(), phase + spiral.angleStep);
    }

    @Override
    public void forget(String key) {
        phases.remove(key);
    }

    @Override
    public void clear() {
        phases.clear();
    }
}
