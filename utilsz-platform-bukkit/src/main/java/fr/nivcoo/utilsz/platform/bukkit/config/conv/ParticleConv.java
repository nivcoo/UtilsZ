package fr.nivcoo.utilsz.platform.bukkit.config.conv;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;

import java.lang.reflect.Field;

public final class ParticleConv implements Converter<Particle> {

    @Override
    public Particle read(Object raw, Particle fallback, Field f) {
        if (raw == null) return fallback;
        String name = String.valueOf(raw).trim().toLowerCase();

        NamespacedKey key = name.contains(":")
                ? NamespacedKey.fromString(name)
                : NamespacedKey.minecraft(name);

        if (key == null) return fallback;

        Particle particle = Registry.PARTICLE_TYPE.get(key);
        return particle != null ? particle : fallback;
    }

    @Override
    public Object write(Particle value, Field f) {
        if (value == null) return null;
        NamespacedKey key = Registry.PARTICLE_TYPE.getKey(value);
        return key != null ? key.asString() : null;
    }
}
