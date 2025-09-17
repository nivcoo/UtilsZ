package fr.nivcoo.utilsz.redis.rpc;

public final class RpcIgnoreException extends RuntimeException {
    public RpcIgnoreException() { super(null, null, false, false); }
}
