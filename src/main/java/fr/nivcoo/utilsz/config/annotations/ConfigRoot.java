package fr.nivcoo.utilsz.config.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigRoot {
    String file();
    String header() default "";
}
