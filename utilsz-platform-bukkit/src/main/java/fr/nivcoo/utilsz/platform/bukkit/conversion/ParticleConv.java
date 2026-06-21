package fr.nivcoo.utilsz.platform.bukkit.conversion;

import fr.nivcoo.utilsz.core.conversion.Converter;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;

import java.lang.reflect.Field;

public class ParticleConv implements Converter<Particle> {

    @Override
    public Particle read(Object raw, Particle fallback, Field field) {
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
    public Object write(Particle value, Field field) {
        if (value == null) return null;
        return value.name();
    }
}
