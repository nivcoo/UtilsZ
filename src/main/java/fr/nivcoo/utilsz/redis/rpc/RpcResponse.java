package fr.nivcoo.utilsz.redis.rpc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RpcResponse {
    Class<?> value();
}
