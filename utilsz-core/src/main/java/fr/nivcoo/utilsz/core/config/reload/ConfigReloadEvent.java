package fr.nivcoo.utilsz.core.config.reload;

import java.nio.file.Path;
import java.util.Objects;

public record ConfigReloadEvent<T>(Path file, T previous, T candidate) {

    public ConfigReloadEvent {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(candidate, "candidate");
    }
}
