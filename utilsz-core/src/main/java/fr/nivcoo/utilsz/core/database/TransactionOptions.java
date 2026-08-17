package fr.nivcoo.utilsz.core.database;

import java.sql.Connection;

public record TransactionOptions(int isolationLevel, int maxAttempts) {

    public TransactionOptions {
        if (isolationLevel != Connection.TRANSACTION_READ_UNCOMMITTED
                && isolationLevel != Connection.TRANSACTION_READ_COMMITTED
                && isolationLevel != Connection.TRANSACTION_REPEATABLE_READ
                && isolationLevel != Connection.TRANSACTION_SERIALIZABLE) {
            throw new IllegalArgumentException("Unsupported transaction isolation level.");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Transaction attempts must be positive.");
        }
    }

    public static TransactionOptions serializable(int maxAttempts) {
        return new TransactionOptions(Connection.TRANSACTION_SERIALIZABLE, maxAttempts);
    }
}
