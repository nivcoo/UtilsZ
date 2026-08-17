package fr.nivcoo.utilsz.core.database;

import java.util.Objects;

@SuppressWarnings("unused")
public sealed interface AtomicUpdate permits AtomicUpdate.Set, AtomicUpdate.Add, AtomicUpdate.Max {

    Object value();

    static Set set(Object value) {
        return new Set(value);
    }

    static Add add(Number value) {
        return new Add(value);
    }

    static Max max(Number value) {
        return new Max(value);
    }

    record Set(Object value) implements AtomicUpdate {
    }

    record Add(Number value) implements AtomicUpdate {
        public Add {
            Objects.requireNonNull(value, "value");
        }
    }

    record Max(Number value) implements AtomicUpdate {
        public Max {
            Objects.requireNonNull(value, "value");
        }
    }
}
