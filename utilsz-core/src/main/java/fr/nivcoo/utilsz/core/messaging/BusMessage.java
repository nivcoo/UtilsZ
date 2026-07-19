package fr.nivcoo.utilsz.core.messaging;

public interface BusMessage {
    default void execute() {
        throw new UnsupportedOperationException(
                "This message requires an explicitly registered BusHandler"
        );
    }
}
