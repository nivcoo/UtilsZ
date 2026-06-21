package fr.nivcoo.utilsz.core.database;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DatabaseRow {

    private final Map<String, Object> values;

    private DatabaseRow(Map<String, Object> values) {
        this.values = values;
    }

    public static DatabaseRow from(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            Object value = rs.getObject(i);
            values.put(label, value);
            values.put(label.toLowerCase(Locale.ROOT), value);
        }
        return new DatabaseRow(values);
    }

    public Object get(String column) {
        if (column == null) return null;
        Object value = values.get(column);
        return value != null ? value : values.get(column.toLowerCase(Locale.ROOT));
    }

    public <T> T get(String column, Class<T> type) {
        return DatabaseCodecs.decode(get(column), type);
    }

    public <T> List<T> getList(String column, Class<T> elementType) {
        return DatabaseCodecs.decodeList(get(column), elementType);
    }

    public String getString(String column) {
        Object value = get(column);
        return value == null ? null : String.valueOf(value);
    }

    public int getInt(String column) {
        Object value = get(column);
        if (value instanceof Number number) return number.intValue();
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    public long getLong(String column) {
        Object value = get(column);
        if (value instanceof Number number) return number.longValue();
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    public boolean getBoolean(String column) {
        Object value = get(column);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    public BigDecimal getBigDecimal(String column) {
        Object value = get(column);
        if (value instanceof BigDecimal decimal) return decimal;
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    public UUID getUuid(String column) {
        return get(column, UUID.class);
    }
}
