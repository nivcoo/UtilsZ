package fr.nivcoo.utilsz.config.conv;


import fr.nivcoo.utilsz.config.annotations.Converter;
import org.bukkit.Particle;

import java.lang.reflect.Field;

public final class ParticleConv implements Converter<Particle> {
    @Override
    public Particle read(Object raw, Particle fb, Field f) {
        if (raw == null) return fb;
        try {
            return Particle.valueOf(String.valueOf(raw).toUpperCase());
        } catch (IllegalArgumentException e) {
            return fb;
        }
    }

    @Override
    public Object write(Particle v, Field f) {
        return v == null ? null : v.name();
    }
}
