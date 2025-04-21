package fr.nivcoo.utilsz.redis;

public interface RedisSerializable {
    void execute();
    default String getAction() {
        RedisAction annotation = this.getClass().getAnnotation(RedisAction.class);
        return annotation != null ? annotation.value() : "unknown";
    }
}