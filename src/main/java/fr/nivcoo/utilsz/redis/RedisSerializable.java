package fr.nivcoo.utilsz.redis;

public interface RedisSerializable {
    void execute();
    default String getAction() {
        RedisAction a = this.getClass().getAnnotation(RedisAction.class);
        return a != null ? a.value() : "unknown";
    }
}
