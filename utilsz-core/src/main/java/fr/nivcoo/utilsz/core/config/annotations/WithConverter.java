package fr.nivcoo.utilsz.core.config.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface WithConverter {
    Class<? extends Converter<?>> value();
}
