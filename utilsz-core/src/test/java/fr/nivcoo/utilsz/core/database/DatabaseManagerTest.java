package fr.nivcoo.utilsz.core.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

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

    @Test
    void modelQueriesQuoteReservedIdentifiers() throws Exception {
        DatabaseManager database = new DatabaseManager(
                DatabaseType.SQLITE,
                null,
                0,
                null,
                null,
                null,
                tempDirectory.resolve("reserved-identifiers.db").toString()
        );

        try {
            ModelRepository<ReservedIdentifiers> repository = database.model(ReservedIdentifiers.MODEL);
            repository.createTable();
            repository.insert(new ReservedIdentifiers(0L, "initial", 1));

            ReservedIdentifiers inserted = repository.find()
                    .where("trigger", "initial")
                    .whereGreaterOrEqual("limit", 1)
                    .whereLessOrEqual("limit", 1)
                    .all()
                    .getFirst();
            assertEquals("initial", inserted.trigger());

            assertEquals(1, repository.update(inserted.select(), Map.of("trigger", "updated")));
            assertEquals("updated", repository.find().where("trigger", "updated").all().getFirst().trigger());
        } finally {
            database.closeConnection();
        }
    }

    private record ReservedIdentifiers(long select, String trigger, int limit) {
        private static final DatabaseModel<ReservedIdentifiers> MODEL = new DatabaseModel<>() {
            @Override
            public ModelSchema<ReservedIdentifiers> schema() {
                return ModelSchema.<ReservedIdentifiers>of("order")
                        .id("select", ReservedIdentifiers::select)
                        .column("trigger", ColumnType.STRING, ReservedIdentifiers::trigger)
                        .column("limit", ColumnType.INT, ReservedIdentifiers::limit);
            }

            @Override
            public ReservedIdentifiers from(DatabaseRow row) {
                return new ReservedIdentifiers(
                        row.getLong("select"),
                        row.getString("trigger"),
                        row.getInt("limit")
                );
            }
        };
    }
}
