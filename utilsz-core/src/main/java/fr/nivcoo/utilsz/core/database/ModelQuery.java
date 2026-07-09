package fr.nivcoo.utilsz.core.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class ModelQuery<T> {

    private final DatabaseManager database;
    private final ModelSchema<T> schema;
    private final RowMapper<T> mapper;
    private final Connection connection;
    private final List<String> where = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();
    private String orderBy;
    private int limit;

    ModelQuery(DatabaseManager database, ModelSchema<T> schema, RowMapper<T> mapper) {
        this(database, schema, mapper, null);
    }

    ModelQuery(DatabaseManager database, ModelSchema<T> schema, RowMapper<T> mapper, Connection connection) {
        this.database = database;
        this.schema = schema;
        this.mapper = mapper;
        this.connection = connection;
    }

    public ModelQuery<T> where(String column, Object value) {
        where.add(column + " = ?");
        params.add(schema.encodeValue(column, value));
        return this;
    }

    public ModelQuery<T> whereLessOrEqual(String column, Object value) {
        where.add(column + " <= ?");
        params.add(schema.encodeValue(column, value));
        return this;
    }

    public ModelQuery<T> whereGreaterOrEqual(String column, Object value) {
        where.add(column + " >= ?");
        params.add(schema.encodeValue(column, value));
        return this;
    }

    public ModelQuery<T> whereRaw(String condition, Object... values) {
        where.add(condition);
        if (values != null) {
            params.addAll(List.of(schema.encodeWhereParams(condition, values)));
        }
        return this;
    }

    public ModelQuery<T> orderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public ModelQuery<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    public List<T> all() throws SQLException {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (String condition : where) {
            joiner.add(condition);
        }
        return database.table(schema.name()).select(
                connection,
                schema,
                mapper,
                joiner.length() == 0 ? null : joiner.toString(),
                orderBy,
                limit,
                params.toArray()
        );
    }
}
