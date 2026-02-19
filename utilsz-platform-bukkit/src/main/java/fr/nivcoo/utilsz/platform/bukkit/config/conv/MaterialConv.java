package fr.nivcoo.utilsz.platform.bukkit.config.conv;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.lang.reflect.Field;

public final class MaterialConv implements Converter<Material> {

    @Override
    public Material read(Object raw, Material fallback, Field f) {
        if (raw == null) return fallback;
        String name = String.valueOf(raw).trim().toLowerCase();

        NamespacedKey key = name.contains(":")
                ? NamespacedKey.fromString(name)
                : NamespacedKey.minecraft(name);

        if (key == null) return fallback;

        Material mat = Registry.MATERIAL.get(key);
        return mat != null ? mat : fallback;
    }

    @Override
    public Object write(Material value, Field f) {
        if (value == null) return null;
        NamespacedKey key = Registry.MATERIAL.getKey(value);
        return key != null ? key.asString() : null;
    }
}
