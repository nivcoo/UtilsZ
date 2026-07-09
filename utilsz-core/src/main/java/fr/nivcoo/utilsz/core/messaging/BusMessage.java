package fr.nivcoo.utilsz.core.messaging;

public interface BusMessage {
    void execute();

    default boolean runOnMainThread() {
        return false;
    }

    default String getAction() {
        BusAction a = this.getClass().getAnnotation(BusAction.class);
        return a != null ? a.value() : "unknown";
    }
}
