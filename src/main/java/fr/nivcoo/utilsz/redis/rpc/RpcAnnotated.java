package fr.nivcoo.utilsz.redis.rpc;

public interface RpcAnnotated {
    default boolean runOnMainThread() { return false; }

    Object handle() throws Exception;
}
