package fr.nivcoo.utilsz.core.config;

import java.util.Objects;

@SuppressWarnings("unused")
public record NamedConfig<T>(String id, String relativePath, T value) {

    public NamedConfig {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath cannot be blank");
        }
        Objects.requireNonNull(value, "value");
    }
}
