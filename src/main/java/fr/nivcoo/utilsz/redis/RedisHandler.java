package fr.nivcoo.utilsz.redis;

@FunctionalInterface
public interface RedisHandler<T extends RedisSerializable> {
    void handle(T message);
}