package fr.nivcoo.utilsz.redis;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RedisAction {
    String value();
    Class<?> response() default Void.class;
    boolean receiveOwnMessages() default false;
}

