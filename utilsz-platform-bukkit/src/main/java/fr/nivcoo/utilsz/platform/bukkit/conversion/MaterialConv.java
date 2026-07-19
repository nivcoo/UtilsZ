package fr.nivcoo.utilsz.platform.bukkit.conversion;

import fr.nivcoo.utilsz.core.conversion.Converter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.lang.reflect.Field;

public class MaterialConv implements Converter<Material> {

    @Override
    public Material read(Object raw, Material fallback, Field field) {
        if (raw == null) return fallback;
        String name = String.valueOf(raw).trim().toLowerCase();

        Material direct = Material.matchMaterial(name);
        if (direct != null) return direct;

        NamespacedKey key = name.contains(":")
                ? NamespacedKey.fromString(name)
                : NamespacedKey.minecraft(name);

        if (key == null) return fallback;

        Material mat = Registry.MATERIAL.get(key);
        return mat != null ? mat : fallback;
    }

    @Override
    public Object write(Material value, Field field) {
        if (value == null) return null;
        return value.name();
    }
}
