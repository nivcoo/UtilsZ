package fr.nivcoo.utilsz.platform.bukkit.conversion;

import fr.nivcoo.utilsz.core.conversion.Converter;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager.Profession;

import java.lang.reflect.Field;
import java.util.Locale;

public class VillagerProfessionConv implements Converter<Profession> {

    @Override
    public Profession read(Object raw, Profession fallback, Field field) {
        if (raw == null) return fallback;

        NamespacedKey key = key(String.valueOf(raw));
        if (key == null) return fallback;

        Profession profession = Registry.VILLAGER_PROFESSION.get(key);
        return profession != null ? profession : fallback;
    }

    @Override
    public Object write(Profession value, Field field) {
        return value == null ? null : value.getKey().asString();
    }

    private NamespacedKey key(String input) {
        if (input == null || input.isBlank()) return null;

        String text = input.trim().toLowerCase(Locale.ROOT);
        return text.contains(":") ? NamespacedKey.fromString(text) : NamespacedKey.minecraft(text);
    }
}
