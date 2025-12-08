package fr.nivcoo.utilsz.messaging;

@FunctionalInterface
public interface BusHandler<T extends BusMessage> {
    void handle(T message);
}
