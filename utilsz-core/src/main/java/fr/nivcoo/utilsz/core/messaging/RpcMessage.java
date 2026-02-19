package fr.nivcoo.utilsz.core.messaging;

public interface RpcMessage {
    default boolean runOnMainThread() {
        return false;
    }

    Object handle() throws Exception;
}
