package fr.nivcoo.utilsz.platform.bukkit.conversion;

import fr.nivcoo.utilsz.core.conversion.Converter;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;

public class PotionEffectTypeConv implements Converter<PotionEffectType> {

    @Override
    public PotionEffectType read(Object raw, PotionEffectType fallback, Field field) {
        if (raw == null) return fallback;
        String name = String.valueOf(raw).trim().toLowerCase();

        NamespacedKey key = name.contains(":")
                ? NamespacedKey.fromString(name)
                : NamespacedKey.minecraft(name);

        if (key == null) return fallback;

        PotionEffectType type = Registry.MOB_EFFECT.get(key);
        return type != null ? type : fallback;
    }

    @Override
    public Object write(PotionEffectType value, Field field) {
        if (value == null) return null;
        NamespacedKey key = Registry.MOB_EFFECT.getKey(value);
        if (key == null) return null;
        return NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.asString();
    }
}
