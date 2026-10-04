package fr.nivcoo.utilsz.core.config.annotations;

import fr.nivcoo.utilsz.core.config.text.TextMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TextFormat {
    TextMode value() default TextMode.AUTO;
}
