package fr.nivcoo.utilsz.config.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
public @interface Polymorphic {
    Class<?> element();
    String  typeKey() default "type";
}
