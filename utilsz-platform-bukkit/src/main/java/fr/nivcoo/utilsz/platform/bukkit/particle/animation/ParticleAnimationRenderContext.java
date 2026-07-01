package fr.nivcoo.utilsz.platform.bukkit.particle.animation;

import fr.nivcoo.utilsz.platform.bukkit.particle.ParticleAnimationConfig;
import fr.nivcoo.utilsz.platform.bukkit.particle.ParticleSpawner;
import org.bukkit.Location;
import org.bukkit.World;

public record ParticleAnimationRenderContext(String key, World world, Location block,
                                             ParticleAnimationConfig config, ParticleSpawner spawner) {
}
