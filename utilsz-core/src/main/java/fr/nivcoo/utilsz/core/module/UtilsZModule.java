package fr.nivcoo.utilsz.core.module;

import fr.nivcoo.utilsz.core.conversion.Converter;
import fr.nivcoo.utilsz.core.database.DatabaseCodec;

import java.util.Map;
import java.util.function.Supplier;

public interface UtilsZModule {
    default boolean isCompatible() {
        return true;
    }

    default int priority() {
        return 0;
    }

    default void onLoad() {
    }

    default Map<Class<?>, Supplier<Converter<?>>> converters() {
        return Map.of();
    }

    default Map<Class<?>, Supplier<DatabaseCodec<?>>> databaseCodecs() {
        return Map.of();
    }
}
