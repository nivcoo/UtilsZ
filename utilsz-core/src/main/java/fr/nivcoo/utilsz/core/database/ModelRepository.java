package fr.nivcoo.utilsz.core.database;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, Object> values = new LinkedHashMap<>();
        for (ModelColumn<T> column : schema.columns()) {
            if (column.generated()) continue;
            values.put(column.name(), column.toDatabase(model));
        }
        return database.insert(connection, schema.name(), values);
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

    public List<T> all() throws SQLException {
        return find().all();
    }
}
