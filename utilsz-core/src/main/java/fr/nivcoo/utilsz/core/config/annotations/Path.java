package fr.nivcoo.utilsz.core.config.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Path {
    String value();
    boolean absolute() default false;
}
