package fr.nivcoo.utilsz.platform.bukkit.particle.animation;

@SuppressWarnings("unused")
public interface ParticleAnimationRenderer {

    void render(ParticleAnimationRenderContext context);

    default void forget(String key) {
    }

    default void clear() {
    }
}
