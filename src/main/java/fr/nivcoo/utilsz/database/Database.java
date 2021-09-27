package fr.nivcoo.utilsz.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    private Connection connection;
    private final String urlBase;
    private final String host;
    private final String database;
    private final String user;
    private final String password;
    private final String port;

    public Database(String urlBase, String host, String database, String user, String password, String port) {
        this.urlBase = urlBase;
        this.host = host;
        this.database = database;
        this.user = user;
        this.password = password;
        this.port = port;
    }

    public void connection() {
        if (!isConnected()) {
            try {
                connection = DriverManager.getConnection(urlBase + host + ":" + port + "/" + database + "?autoReconnect=true&useUnicode=yes", user, password);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void disconnect() {
        if (isConnected()) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException ignored) {
            return false;
        }

    }


}
