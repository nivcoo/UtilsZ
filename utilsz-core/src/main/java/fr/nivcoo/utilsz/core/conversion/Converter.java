package fr.nivcoo.utilsz.core.conversion;

import java.lang.reflect.Field;

public interface Converter<T> {

    T read(Object raw, T fallback, Field field);

    Object write(T value, Field field);
}
