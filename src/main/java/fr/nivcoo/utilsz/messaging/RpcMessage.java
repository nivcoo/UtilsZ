package fr.nivcoo.utilsz.messaging;

public interface RpcMessage {
    default boolean runOnMainThread() {
        return false;
    }

    Object handle() throws Exception;
}
