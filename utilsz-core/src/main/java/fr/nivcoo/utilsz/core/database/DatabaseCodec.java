package fr.nivcoo.utilsz.core.database;

public interface DatabaseCodec<T> {

    Class<T> type();

    Object toDatabase(T value);

    T fromDatabase(Object value);
}
