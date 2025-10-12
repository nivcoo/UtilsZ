package fr.nivcoo.utilsz.config.conv;

import fr.nivcoo.utilsz.config.annotations.Converter;
import org.bukkit.Sound;

import java.lang.reflect.Field;

public final class SoundConv implements Converter<Sound> {
    @Override
    public Sound read(Object raw, Sound fb, Field f) {
        if (raw == null) return fb;
        try {
            return Sound.valueOf(String.valueOf(raw).toUpperCase());
        } catch (IllegalArgumentException e) {
            return fb;
        }
    }

    @Override
    public Object write(Sound v, Field f) {
        return v == null ? null : v.name();
    }
}