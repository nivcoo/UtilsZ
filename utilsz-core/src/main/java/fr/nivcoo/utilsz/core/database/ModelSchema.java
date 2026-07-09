package fr.nivcoo.utilsz.core.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

@SuppressWarnings("unused")
public final class ModelSchema<T> {

    private final String name;
    private final List<ModelColumn<T>> columns = new ArrayList<>();
    private final List<TableConstraintDefinition> constraints = new ArrayList<>();
    private final List<ModelIndex> indexes = new ArrayList<>();
    private String idColumn;

    private ModelSchema(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be blank.");
        }
        this.name = name;
    }

    public static <T> ModelSchema<T> of(String name) {
        return new ModelSchema<>(name);
    }

    public ModelSchema<T> id(String name) {
        return id(name, null);
    }

    public ModelSchema<T> id(String name, Function<T, Object> getter) {
        this.idColumn = name;
        String autoIncrement = "PRIMARY KEY {auto_increment}";
        columns.add(new ModelColumn<>(name, ColumnType.ID, 0, autoIncrement, null, getter, true));
        return this;
    }

    public ModelSchema<T> column(String name, ColumnType type, Function<T, Object> getter) {
        return column(name, type, "NOT NULL", getter);
    }

    public ModelSchema<T> column(String name, ColumnType type, Class<?> valueType, Function<T, Object> getter) {
        return column(name, type, valueType, "NOT NULL", getter);
    }

    public ModelSchema<T> column(String name, ColumnType type, int length, Function<T, Object> getter) {
        return column(name, type, length, "NOT NULL", getter);
    }

    public ModelSchema<T> column(String name, ColumnType type, int length, Class<?> valueType,
                                 Function<T, Object> getter) {
        return column(name, type, length, valueType, "NOT NULL", getter);
    }

    public ModelSchema<T> column(String name, ColumnType type, String constraints, Function<T, Object> getter) {
        return column(name, type, 0, constraints, getter);
    }

    public ModelSchema<T> column(String name, ColumnType type, Class<?> valueType, String constraints,
                                 Function<T, Object> getter) {
        return column(name, type, 0, valueType, constraints, getter);
    }

    public ModelSchema<T> column(String name, ColumnType type, int length, String constraints,
                                 Function<T, Object> getter) {
        return column(name, type, length, null, constraints, getter);
    }

    public ModelSchema<T> column(String name, ColumnType type, int length, Class<?> valueType, String constraints,
                                 Function<T, Object> getter) {
        columns.add(new ModelColumn<>(name, type, length, constraints, valueType, getter, false));
        return this;
    }

    public ModelSchema<T> constraint(String constraint) {
        constraints.add(new TableConstraintDefinition(constraint));
        return this;
    }

    public ModelSchema<T> index(String name, String... columns) {
        indexes.add(new ModelIndex(name, List.of(columns)));
        return this;
    }

    public String name() {
        return name;
    }

    public List<ModelColumn<T>> columns() {
        return List.copyOf(columns);
    }

    public List<ModelIndex> indexes() {
        return List.copyOf(indexes);
    }

    public String idColumn() {
        return idColumn;
    }

    List<Object> definitions(DatabaseType databaseType) {
        List<Object> definitions = new ArrayList<>();
        for (ModelColumn<T> column : columns) {
            definitions.add(column.definition(databaseType));
        }
        definitions.addAll(constraints);
        return definitions;
    }

    String selectColumns() {
        StringJoiner joiner = new StringJoiner(", ");
        for (ModelColumn<T> column : columns) {
            joiner.add(column.name());
        }
        return joiner.toString();
    }

    String idWhere() {
        if (idColumn == null || idColumn.isBlank()) {
            throw new IllegalStateException("Table " + name + " does not define an id column.");
        }
        return idColumn + " = ?";
    }

    Object encodeValue(String column, Object value) {
        ModelColumn<T> modelColumn = column(column);
        return modelColumn == null ? DatabaseCodecs.encode(value) : DatabaseCodecs.encode(value, modelColumn.valueType());
    }

    ColumnType columnType(String column) {
        ModelColumn<T> modelColumn = column(column);
        return modelColumn == null ? null : modelColumn.type();
    }

    Map<String, Object> encodeValues(Map<String, ?> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            out.put(entry.getKey(), encodeValue(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    Object[] encodeWhereParams(String where, Object... params) {
        if (params == null || params.length == 0) return params;

        Object[] out = new Object[params.length];
        int searchFrom = 0;
        for (int i = 0; i < params.length; i++) {
            int placeholder = where == null ? -1 : where.indexOf('?', searchFrom);
            String column = placeholder < 0 ? null : columnBeforePlaceholder(where, placeholder);
            out[i] = column == null ? DatabaseCodecs.encode(params[i]) : encodeValue(column, params[i]);
            searchFrom = placeholder < 0 ? searchFrom : placeholder + 1;
        }
        return out;
    }

    private ModelColumn<T> column(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = normalizeColumnName(name);
        for (ModelColumn<T> column : columns) {
            if (normalizeColumnName(column.name()).equals(normalized)) return column;
        }
        return null;
    }

    private static String columnBeforePlaceholder(String where, int placeholder) {
        int end = placeholder - 1;
        while (end >= 0 && Character.isWhitespace(where.charAt(end))) end--;
        while (end >= 0 && isOperatorChar(where.charAt(end))) end--;
        while (end >= 0 && Character.isWhitespace(where.charAt(end))) end--;
        if (end < 0) return null;

        int start = end;
        while (start >= 0 && isIdentifierChar(where.charAt(start))) start--;
        if (start == end) return null;
        return where.substring(start + 1, end + 1);
    }

    private static boolean isOperatorChar(char value) {
        return value == '=' || value == '<' || value == '>' || value == '!';
    }

    private static boolean isIdentifierChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '`' || value == '.';
    }

    private static String normalizeColumnName(String name) {
        String clean = name.trim();
        int dot = clean.lastIndexOf('.');
        if (dot >= 0) clean = clean.substring(dot + 1);
        clean = clean.replace("`", "");
        return clean.toLowerCase(Locale.ROOT);
    }
}
