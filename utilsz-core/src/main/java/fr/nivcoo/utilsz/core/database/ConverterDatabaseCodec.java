package fr.nivcoo.utilsz.core.database;

import fr.nivcoo.utilsz.core.conversion.Converter;

import java.util.function.Supplier;

public final class ConverterDatabaseCodec<T> implements DatabaseCodec<T> {

    private final Class<T> type;
    private final Supplier<? extends Converter<T>> converter;

    public ConverterDatabaseCodec(Class<T> type, Supplier<? extends Converter<T>> converter) {
        this.type = type;
        this.converter = converter;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public Object toDatabase(T value) {
        return converter.get().write(value, null);
    }

    @Override
    public T fromDatabase(Object value) {
        return converter.get().read(value, null, null);
    }
}
