package fr.nivcoo.utilsz.core.messaging;

@FunctionalInterface
public interface BusHandler<T extends BusMessage> {
    void handle(T message);
}
