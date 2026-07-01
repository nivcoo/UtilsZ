package fr.nivcoo.utilsz.platform.bukkit.particle.animation;

import fr.nivcoo.utilsz.platform.bukkit.particle.ParticleAnimationConfig;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public final class TrailSpiralParticleAnimation implements ParticleAnimationRenderer {

    private final Map<String, State> states = new HashMap<>();

    @Override
    public void render(ParticleAnimationRenderContext context) {
        ParticleAnimationConfig.Spiral spiral = context.config().spiral;
        State state = states.computeIfAbsent(context.key(), ignored -> new State(context.block().getBlockY()));
        state.align(context.block().getBlockY());
        double centerX = context.block().getBlockX() + 0.5;
        double centerZ = context.block().getBlockZ() + 0.5;
        double radius = Math.max(0.0, spiral.radius);
        Location next = new Location(context.world(),
                centerX + radius * Math.cos(state.angle),
                state.height,
                centerZ + radius * Math.sin(state.angle));
        state.add(next, Math.clamp(spiral.trailLength, 1, 24));
        for (Location location : state.trail) {
            context.spawner().spawn(context.world(), Particle.DUST, location, 1, 0, 0, 0, 0,
                    context.config().effect.color, spiral.dustSize);
        }
        state.advance(context.block().getBlockY(), spiral.angleStep, spiral.heightStep);
    }

    @Override
    public void forget(String key) {
        states.remove(key);
    }

    @Override
    public void clear() {
        states.clear();
    }

    private static final class State {
        private double angle;
        private double height;
        private boolean descending;
        private final Queue<Location> trail = new ArrayDeque<>();

        private State(double baseY) {
            this.height = baseY;
        }

        private void align(double baseY) {
            if (height < baseY || height > baseY + 1) {
                height = baseY;
                descending = false;
                trail.clear();
            }
        }

        private void add(Location location, int max) {
            while (trail.size() >= max) trail.poll();
            trail.add(location);
        }

        private void advance(int baseY, double angleStep, double heightStep) {
            angle += angleStep;
            if (angle > 360.0) angle = 0.0;
            if (height > baseY + 1) descending = true;
            else if (height < baseY) descending = false;
            height += descending ? -heightStep : heightStep;
        }
    }
}
