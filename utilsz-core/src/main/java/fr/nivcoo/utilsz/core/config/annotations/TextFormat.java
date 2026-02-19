package fr.nivcoo.utilsz.core.config.annotations;

import fr.nivcoo.utilsz.core.config.text.TextMode;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TextFormat {
    TextMode value() default TextMode.AUTO;
}
