package fr.nivcoo.utilsz.core.config.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SaveOnLoad {
    boolean value() default true;
}
