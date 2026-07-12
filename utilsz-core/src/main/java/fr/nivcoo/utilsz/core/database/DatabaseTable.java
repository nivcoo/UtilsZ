package fr.nivcoo.utilsz.core.database;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public final class DatabaseTable {

    private final DatabaseManager database;
    private final String name;

    DatabaseTable(DatabaseManager database, String name) {
        this.database = database;
        this.name = name;
    }

    public int insert(Map<String, ?> values) throws SQLException {
        return database.insert(name, values);
    }

    public int insert(Connection connection, Map<String, ?> values) throws SQLException {
        return database.insert(connection, name, values);
    }

    public int update(Map<String, ?> values, String where, Object... params) throws SQLException {
        return database.update(name, values, where, params);
    }

    public int delete(String where, Object... params) throws SQLException {
        return database.delete(name, where, params);
    }

    public int delete(Connection connection, String where, Object... params) throws SQLException {
        return database.delete(connection, name, where, params);
    }

    public boolean exists(String where, Object... params) throws SQLException {
        return database.exists(name, where, params);
    }

    public boolean exists(Connection connection, String where, Object... params) throws SQLException {
        return database.exists(connection, name, where, params);
    }

    public <T> List<T> select(String columns, RowMapper<T> mapper, String where, String orderBy,
                              int limit, Object... params) throws SQLException {
        return select(null, columns, mapper, where, orderBy, limit, params);
    }

    public <T> List<T> select(Connection connection, String columns, RowMapper<T> mapper, String where, String orderBy,
                              int limit, Object... params) throws SQLException {
        return database.query(connection, selectSql(columns, where, orderBy, limit), mapper, params);
    }

    <T> List<T> select(Connection connection, ModelSchema<?> schema, RowMapper<T> mapper, String where, String orderBy,
                       int limit, Object... params) throws SQLException {
        return database.queryModel(connection, selectSql(schema.selectColumns(), where, orderBy, limit), schema, mapper, params);
    }

    private String selectSql(String columns, String where, String orderBy, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(columns == null || columns.isBlank() ? "*" : columns)
                .append(" FROM ")
                .append(DatabaseManager.quote(name));
        if (where != null && !where.isBlank()) {
            sql.append(" WHERE ").append(where);
        }
        if (orderBy != null && !orderBy.isBlank()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return sql.toString();
    }
}
