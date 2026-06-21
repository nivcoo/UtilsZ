package fr.nivcoo.utilsz.core.config.annotations;

import fr.nivcoo.utilsz.core.conversion.Converter;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface WithConverter {
    Class<? extends Converter<?>> value();
}
