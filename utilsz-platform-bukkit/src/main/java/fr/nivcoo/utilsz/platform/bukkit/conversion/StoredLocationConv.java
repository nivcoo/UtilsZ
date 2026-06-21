package fr.nivcoo.utilsz.platform.bukkit.conversion;

import fr.nivcoo.utilsz.core.conversion.Converter;
import fr.nivcoo.utilsz.platform.bukkit.location.StoredLocation;
import org.bukkit.Location;

import java.lang.reflect.Field;

public final class StoredLocationConv implements Converter<StoredLocation> {

    @Override
    public StoredLocation read(Object raw, StoredLocation fallback, Field field) {
        if (raw == null) return fallback;
        if (raw instanceof StoredLocation storedLocation) return storedLocation;
        if (raw instanceof Location location) return StoredLocation.from(location);
        String text = String.valueOf(raw);
        return text.isBlank() ? fallback : StoredLocation.parse(text);
    }

    @Override
    public Object write(StoredLocation value, Field field) {
        return value == null ? null : value.serialize();
    }
}
