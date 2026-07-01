package fr.nivcoo.utilsz.platform.bukkit.particle.animation;

import fr.nivcoo.utilsz.platform.bukkit.particle.ParticleEffectConfig;
import org.bukkit.Location;
import org.bukkit.Particle;

public final class StaticParticleAnimation implements ParticleAnimationRenderer {

    @Override
    public void render(ParticleAnimationRenderContext context) {
        ParticleEffectConfig effect = context.config().effect;
        if (effect == null || effect.particles == null || effect.particles.isEmpty()) return;
        Location center = context.block().clone().add(0.5, context.config().yOffset, 0.5);
        int count = Math.max(1, effect.count);
        for (Particle particle : effect.particles) {
            context.spawner().spawn(context.world(), particle, center, count,
                    effect.offsetX, effect.offsetY, effect.offsetZ, effect.extra, effect.color, effect.dustSize);
        }
    }
}
