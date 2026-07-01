package fr.nivcoo.utilsz.platform.bukkit.particle;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

@SuppressWarnings("unused")
public final class ParticleSpawner {

    public void spawn(World world, Particle particle, Location location, int count,
                      double offsetX, double offsetY, double offsetZ, double extra,
                      ParticleColor color, float dustSize) {
        if (world == null || particle == null || location == null) return;
        if (particle == Particle.INSTANT_EFFECT) {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, new Particle.Spell(Color.WHITE, 1.0f));
            return;
        }
        if (particle == Particle.DUST) {
            ParticleColor dustColor = color == null ? new ParticleColor() : color;
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, new Particle.DustOptions(dustColor.color(), dustSize));
            return;
        }
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }
}
