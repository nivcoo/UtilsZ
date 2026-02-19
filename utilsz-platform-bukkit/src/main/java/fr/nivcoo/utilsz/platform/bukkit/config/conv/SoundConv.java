package fr.nivcoo.utilsz.platform.bukkit.config.conv;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.lang.reflect.Field;

public final class SoundConv implements Converter<Sound> {
    @Override
    public Sound read(Object raw, Sound fallback, Field f) {
        if (raw == null) return fallback;
        String name = String.valueOf(raw).trim().toLowerCase();

        NamespacedKey key = name.contains(":")
                ? NamespacedKey.fromString(name)
                : NamespacedKey.minecraft(name);

        if (key == null) return fallback;

        Sound sound = Registry.SOUNDS.get(key);
        return sound != null ? sound : fallback;
    }

    @Override
    public Object write(Sound value, Field f) {
        if (value == null) return null;
        NamespacedKey key = Registry.SOUNDS.getKey(value);
        return key != null ? key.asString() : null;
    }
}