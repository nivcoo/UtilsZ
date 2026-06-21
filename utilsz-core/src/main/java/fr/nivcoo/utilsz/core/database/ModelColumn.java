package fr.nivcoo.utilsz.core.database;

import java.util.function.Function;

public record ModelColumn<T>(String name, ColumnType type, int length, String constraints, Class<?> valueType,
                             Function<T, Object> getter, boolean generated) {

    public TypedColumnDefinition definition(DatabaseType databaseType) {
        String resolvedConstraints = constraints;
        if (resolvedConstraints != null && resolvedConstraints.contains("{auto_increment}")) {
            String autoIncrement = databaseType == DatabaseType.SQLITE ? "AUTOINCREMENT" : "AUTO_INCREMENT";
            resolvedConstraints = resolvedConstraints.replace("{auto_increment}", autoIncrement);
        }
        return new TypedColumnDefinition(name, type, length, resolvedConstraints);
    }

    public Object toDatabase(T model) {
        if (getter == null) return null;
        Object value = getter.apply(model);
        return DatabaseCodecs.encode(value, valueType);
    }
}
