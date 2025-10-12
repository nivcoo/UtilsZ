package fr.nivcoo.utilsz.config.conv;

import fr.nivcoo.utilsz.config.annotations.Converter;
import org.bukkit.Material;

import java.lang.reflect.Field;

public final class MaterialConv implements Converter<Material> {
    @Override
    public Material read(Object raw, Material fb, Field f) {
        if (raw == null) return fb;
        Material m = Material.matchMaterial(String.valueOf(raw));
        return m != null ? m : fb;
    }

    @Override
    public Object write(Material v, Field f) {
        return v == null ? null : v.name();
    }
}
