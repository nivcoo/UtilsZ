package fr.nivcoo.utilsz.core.database;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModelRepository<T> {

    private final DatabaseManager database;
    private final Table<T> table;

    ModelRepository(DatabaseManager database, Table<T> table) {
        this.database = database;
        this.table = table;
    }

    public void createTable() throws SQLException {
        database.createTable(table.name(), table.definitions(database.getType()));
        for (ModelIndex index : table.indexes()) {
            database.createIndexIfAbsent(table.name(), index.name(), index.columns());
        }
    }

    public int insert(T model) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ModelColumn<T> column : table.columns()) {
            if (column.generated()) continue;
            values.put(column.name(), column.toDatabase(model));
        }
        return database.insert(table.name(), values);
    }

    public int update(Object id, Map<String, ?> values) throws SQLException {
        return update(values, table.idWhere(), id);
    }

    public int update(Map<String, ?> values, String where, Object... params) throws SQLException {
        return database.update(table.name(), values, where, params);
    }

    public int delete(String where, Object... params) throws SQLException {
        return database.delete(table.name(), where, params);
    }

    public boolean exists(String where, Object... params) throws SQLException {
        return database.exists(table.name(), where, params);
    }

    public ModelQuery<T> find() {
        return new ModelQuery<>(database, table);
    }

    public List<T> all() throws SQLException {
        return find().all();
    }
}
