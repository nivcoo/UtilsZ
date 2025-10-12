package fr.nivcoo.utilsz.config.annotations;

import java.lang.reflect.Field;

public interface Converter<T> {
    T read(Object raw, T fallback, Field f);
    Object write(T value, Field f);
}