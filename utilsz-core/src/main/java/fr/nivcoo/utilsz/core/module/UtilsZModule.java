package fr.nivcoo.utilsz.core.module;

import fr.nivcoo.utilsz.core.config.annotations.Converter;

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
}
