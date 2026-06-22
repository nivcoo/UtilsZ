package fr.nivcoo.utilsz.core.database;

@SuppressWarnings("unused")
public abstract class DatabaseModel<T> {

    public abstract ModelSchema<T> schema();

    public abstract T from(DatabaseRow row);
}
