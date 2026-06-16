package fr.nivcoo.utilsz.core.database;

import java.sql.SQLException;

@FunctionalInterface
public interface RowMapper<T> {

    T map(DatabaseRow row) throws SQLException;
}
