package fr.nivcoo.utilsz.platform.bukkit.particle;

@SuppressWarnings("unused")
public final class ParticleAnimationConfig {

    public boolean enabled = true;
    public ParticleAnimationType animation = ParticleAnimationType.STATIC;
    public int intervalTicks = 20;
    public int itemsPerTick = 80;
    public double yOffset = 0.5;
    public ParticleEffectConfig effect = new ParticleEffectConfig();
    public Spiral spiral = new Spiral();

    public static ParticleAnimationConfig disabled() {
        ParticleAnimationConfig config = new ParticleAnimationConfig();
        config.enabled = false;
        return config;
    }

    public static ParticleAnimationConfig staticAnimation(ParticleEffectConfig effect) {
        ParticleAnimationConfig config = new ParticleAnimationConfig();
        config.animation = ParticleAnimationType.STATIC;
        config.effect = effect;
        return config;
    }

    public static ParticleAnimationConfig spiral(ParticleColor color) {
        ParticleAnimationConfig config = new ParticleAnimationConfig();
        config.animation = ParticleAnimationType.SPIRAL;
        config.effect.color = color;
        return config;
    }

    @SuppressWarnings("unused")
    public static final class Spiral {
        public double radius = 0.75;
        public double angleStep = 0.25;
        public double heightStep = 0.05;
        public double height = 1.0;
        public double pointSpacing = 0.45;
        public int trailLength = 3;
        public float dustSize = 1.0F;
    }
}
