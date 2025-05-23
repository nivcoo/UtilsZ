package fr.nivcoo.utilsz.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * General interface for all database types.
 */
public interface DatabaseProvider {
    void connect() throws SQLException;

    Connection getConnection() throws SQLException;

    void close();

    boolean isConnected();

    void executeUpdate(String query) throws SQLException;

    void executeBatch(List<String> queries) throws SQLException;

    void createTable(String tableName, List<Object> elements) throws SQLException;
}
