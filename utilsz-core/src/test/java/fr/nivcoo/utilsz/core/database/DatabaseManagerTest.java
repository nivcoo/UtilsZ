package fr.nivcoo.utilsz.core.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void currentTimeMillisUsesDatabaseClockWithOwnedAndTransactionConnections() throws Exception {
        DatabaseManager database = new DatabaseManager(
                DatabaseType.SQLITE,
                null,
                0,
                null,
                null,
                null,
                tempDirectory.resolve("clock.db").toString()
        );

        try {
            long before = System.currentTimeMillis() - 1_000L;
            long ownedConnectionTime = database.currentTimeMillis();
            long transactionTime = database.transaction(database::currentTimeMillis);
            long after = System.currentTimeMillis() + 1_000L;

            assertTrue(ownedConnectionTime >= before && ownedConnectionTime <= after);
            assertTrue(transactionTime >= before && transactionTime <= after);
        } finally {
            database.closeConnection();
        }
    }

    @Test
    void currentTimeMillisQueriesMatchSupportedDialects() {
        assertTrue(DatabaseManager.currentTimeMillisQuery(DatabaseType.SQLITE).contains("julianday('now')"));
        assertEquals(
                "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)",
                DatabaseManager.currentTimeMillisQuery(DatabaseType.MYSQL)
        );
        assertEquals(
                DatabaseManager.currentTimeMillisQuery(DatabaseType.MYSQL),
                DatabaseManager.currentTimeMillisQuery(DatabaseType.MARIADB)
        );
    }
}
