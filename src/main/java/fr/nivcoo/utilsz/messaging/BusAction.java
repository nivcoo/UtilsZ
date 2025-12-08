package fr.nivcoo.utilsz.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BusAction {
    String value();
    Class<?> response() default Void.class;
    boolean receiveOwnMessages() default false;
}
