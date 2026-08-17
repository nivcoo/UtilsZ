package fr.nivcoo.utilsz.core.database;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModelRepository<T> {

    private final DatabaseManager database;
    private final ModelSchema<T> schema;
    private final RowMapper<T> mapper;

    ModelRepository(DatabaseManager database, DatabaseModel<T> model) {
        this.database = database;
        this.schema = model.schema();
        this.mapper = model::from;
    }

    public void createTable() throws SQLException {
        database.createTable(schema.name(), schema.definitions(database.getType()));
        for (ModelIndex index : schema.indexes()) {
            database.createIndexIfAbsent(schema.name(), index.name(), index.columns());
        }
    }

    public int insert(T model) throws SQLException {
        return insert(null, model);
    }

    public int insert(Connection connection, T model) throws SQLException {
        return database.insert(connection, schema.name(), insertValues(model));
    }

    public boolean insertIfAbsent(T model) throws SQLException {
        return insertIfAbsent(null, model);
    }

    public boolean insertIfAbsent(Connection connection, T model) throws SQLException {
        return database.insertIfAbsent(connection, schema.name(), insertValues(model));
    }

    public long insertReturningId(T model) throws SQLException {
        return insertReturningId(null, model);
    }

    public long insertReturningId(Connection connection, T model) throws SQLException {
        if (schema.idColumn() == null || schema.idColumn().isBlank()) {
            throw new IllegalStateException("Table " + schema.name() + " does not define a generated id column.");
        }
        return database.insertReturningId(connection, schema.name(), insertValues(model));
    }

    private Map<String, Object> insertValues(T model) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ModelColumn<T> column : schema.columns()) {
            if (column.generated()) continue;
            values.put(column.name(), column.toDatabase(model));
        }
        return values;
    }

    public int update(Object id, Map<String, ?> values) throws SQLException {
        return update(values, schema.idWhere(), schema.encodeValue(schema.idColumn(), id));
    }

    public int update(Map<String, ?> values, String where, Object... params) throws SQLException {
        return database.update(schema.name(), schema.encodeValues(values), where, schema.encodeWhereParams(where, params));
    }

    public int update(Connection connection, Map<String, ?> values, String where, Object... params) throws SQLException {
        return database.update(connection, schema.name(), schema.encodeValues(values), where, schema.encodeWhereParams(where, params));
    }

    public int updateAtomic(Map<String, ? extends AtomicUpdate> updates,
                            String where, Object... params) throws SQLException {
        return database.updateAtomic(
                schema.name(), encodeAtomicUpdates(updates), where, schema.encodeWhereParams(where, params));
    }

    public int updateAtomic(Connection connection, Map<String, ? extends AtomicUpdate> updates,
                            String where, Object... params) throws SQLException {
        return database.updateAtomic(
                connection, schema.name(), encodeAtomicUpdates(updates), where, schema.encodeWhereParams(where, params));
    }

    private Map<String, AtomicUpdate> encodeAtomicUpdates(Map<String, ? extends AtomicUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("Cannot perform an empty atomic update.");
        }

        Map<String, AtomicUpdate> encoded = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends AtomicUpdate> entry : updates.entrySet()) {
            String column = entry.getKey();
            ColumnType columnType = schema.columnType(column);
            if (columnType == null) {
                throw new IllegalArgumentException(
                        "Column " + column + " is not declared by model table " + schema.name() + ".");
            }

            AtomicUpdate update = Objects.requireNonNull(
                    entry.getValue(), "Atomic update for column " + column);
            Object value = schema.encodeValue(column, update.value());
            encoded.put(column, switch (update) {
                case AtomicUpdate.Set ignored -> AtomicUpdate.set(value);
                case AtomicUpdate.Add ignored -> AtomicUpdate.add(
                        requireNumericValue(column, columnType, value, "add"));
                case AtomicUpdate.Max ignored -> AtomicUpdate.max(
                        requireNumericValue(column, columnType, value, "max"));
            });
        }
        return encoded;
    }

    private Number requireNumericValue(String column, ColumnType columnType, Object value, String operation) {
        boolean numericColumn = switch (columnType) {
            case ID, INT, LONG, DOUBLE, FLOAT -> true;
            default -> false;
        };
        if (!numericColumn || !(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Atomic " + operation + " requires a numeric model column, but "
                            + column + " uses " + columnType + ".");
        }
        return number;
    }

    public int delete(String where, Object... params) throws SQLException {
        return database.delete(schema.name(), where, schema.encodeWhereParams(where, params));
    }

    public int delete(Connection connection, String where, Object... params) throws SQLException {
        return database.delete(connection, schema.name(), where, schema.encodeWhereParams(where, params));
    }

    public int clear() throws SQLException {
        return database.delete(schema.name(), null);
    }

    public boolean exists(String where, Object... params) throws SQLException {
        return database.exists(schema.name(), where, schema.encodeWhereParams(where, params));
    }

    public boolean exists(Connection connection, String where, Object... params) throws SQLException {
        return database.exists(connection, schema.name(), where, schema.encodeWhereParams(where, params));
    }

    public ModelQuery<T> find() {
        return new ModelQuery<>(database, schema, mapper);
    }

    public ModelQuery<T> find(Connection connection) {
        return new ModelQuery<>(database, schema, mapper, connection);
    }

    public List<T> all() throws SQLException {
        return find().all();
    }
}
