package fr.nivcoo.utilsz.core.messaging;

public interface RpcMessage {
    Object handle() throws Exception;
}
