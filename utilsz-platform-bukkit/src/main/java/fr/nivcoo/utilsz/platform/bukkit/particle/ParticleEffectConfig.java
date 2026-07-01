package fr.nivcoo.utilsz.platform.bukkit.particle;

import org.bukkit.Particle;

import java.util.List;

@SuppressWarnings("unused")
public final class ParticleEffectConfig {

    public List<Particle> particles = List.of();
    public int count = 5;
    public double offsetX = 0.4;
    public double offsetY = 0.4;
    public double offsetZ = 0.4;
    public double extra = 0.0;
    public float dustSize = 1.0F;
    public ParticleColor color = new ParticleColor();

    public ParticleEffectConfig() {
    }

    public ParticleEffectConfig(List<Particle> particles) {
        this.particles = particles;
    }
}
