package fr.nivcoo.utilsz.core.database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

public final class Table<T> {

    private final String name;
    private final RowMapper<T> mapper;
    private final List<ModelColumn<T>> columns = new ArrayList<>();
    private final List<TableConstraintDefinition> constraints = new ArrayList<>();
    private final List<ModelIndex> indexes = new ArrayList<>();
    private String idColumn;

    private Table(String name, RowMapper<T> mapper) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be blank.");
        }
        this.name = name;
        this.mapper = mapper;
    }

    public static <T> Table<T> of(String name, RowMapper<T> mapper) {
        return new Table<>(name, mapper);
    }

    public Table<T> id(String name) {
        return id(name, null);
    }

    public Table<T> id(String name, Function<T, Object> getter) {
        this.idColumn = name;
        String autoIncrement = "PRIMARY KEY {auto_increment}";
        columns.add(new ModelColumn<>(name, ColumnType.ID, 0, autoIncrement, getter, true));
        return this;
    }

    public Table<T> column(String name, ColumnType type, Function<T, Object> getter) {
        return column(name, type, "NOT NULL", getter);
    }

    public Table<T> column(String name, ColumnType type, int length, Function<T, Object> getter) {
        return column(name, type, length, "NOT NULL", getter);
    }

    public Table<T> column(String name, ColumnType type, String constraints, Function<T, Object> getter) {
        return column(name, type, 0, constraints, getter);
    }

    public Table<T> column(String name, ColumnType type, int length, String constraints, Function<T, Object> getter) {
        columns.add(new ModelColumn<>(name, type, length, constraints, getter, false));
        return this;
    }

    public Table<T> constraint(String constraint) {
        constraints.add(new TableConstraintDefinition(constraint));
        return this;
    }

    public Table<T> index(String name, String... columns) {
        indexes.add(new ModelIndex(name, List.of(columns)));
        return this;
    }

    public String name() {
        return name;
    }

    public RowMapper<T> mapper() {
        return mapper;
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
}
