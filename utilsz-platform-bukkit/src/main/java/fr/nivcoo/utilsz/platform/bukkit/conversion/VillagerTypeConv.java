package fr.nivcoo.utilsz.platform.bukkit.conversion;

import fr.nivcoo.utilsz.core.conversion.Converter;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager.Type;

import java.lang.reflect.Field;
import java.util.Locale;

public class VillagerTypeConv implements Converter<Type> {

    @Override
    public Type read(Object raw, Type fallback, Field field) {
        if (raw == null) return fallback;

        NamespacedKey key = key(String.valueOf(raw));
        if (key == null) return fallback;

        Type type = Registry.VILLAGER_TYPE.get(key);
        return type != null ? type : fallback;
    }

    @Override
    public Object write(Type value, Field field) {
        return value == null ? null : value.getKey().asString();
    }

    private NamespacedKey key(String input) {
        if (input == null || input.isBlank()) return null;

        String text = input.trim().toLowerCase(Locale.ROOT);
        return text.contains(":") ? NamespacedKey.fromString(text) : NamespacedKey.minecraft(text);
    }
}
