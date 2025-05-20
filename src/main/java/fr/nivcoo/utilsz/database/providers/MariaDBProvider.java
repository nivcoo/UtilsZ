package fr.nivcoo.utilsz.database.providers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.nivcoo.utilsz.database.ColumnDefinition;
import fr.nivcoo.utilsz.database.DatabaseProvider;
import fr.nivcoo.utilsz.database.TableConstraintDefinition;

import java.sql.*;
import java.util.List;

public class MariaDBProvider implements DatabaseProvider {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    private HikariDataSource dataSource;

    public MariaDBProvider(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public void connect() {
        if (dataSource != null && !dataSource.isClosed()) return;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mariadb://%s:%d/%s", host, port, database));
        config.setUsername(username);
        config.setPassword(password);

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300_000); // 5 min
        config.setMaxLifetime(1_800_000); // 30 min
        config.setConnectionTimeout(10_000); // 10 sec

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public void executeUpdate(String query) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(query);
        }
    }

    @Override
    public ResultSet executeQuery(String query) throws SQLException {
        Connection conn = getConnection();
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(query);
    }

    @Override
    public void executeBatch(List<String> queries) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String q : queries) stmt.addBatch(q);
            stmt.executeBatch();
        }
    }

    @Override
    public PreparedStatement prepareStatement(String query) throws SQLException {
        return getConnection().prepareStatement(query);
    }

    @Override
    public void createTable(String tableName, List<Object> elements) throws SQLException {
        StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + tableName + "` (");

        for (int i = 0; i < elements.size(); i++) {
            Object element = elements.get(i);

            if (element instanceof ColumnDefinition(String name, String type, String constraints)) {
                query.append("`").append(name).append("` ").append(mapType(type));
                if (constraints != null && !constraints.isEmpty()) {
                    query.append(" ").append(constraints);
                }
            } else if (element instanceof TableConstraintDefinition constraint) {
                query.append(constraint.constraint());
            } else {
                throw new IllegalArgumentException("Unknown table element: " + element.getClass());
            }

            if (i < elements.size() - 1) query.append(", ");
        }

        query.append(");");
        executeUpdate(query.toString());
    }

    private String mapType(String type) {
        return switch (type.toUpperCase()) {
            case "TEXT" -> "VARCHAR(255)";
            case "INTEGER" -> "INT";
            case "REAL" -> "DOUBLE";
            case "BLOB" -> "LONGBLOB";
            default -> type;
        };
    }
}
