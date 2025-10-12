package fr.nivcoo.utilsz.config.annotations;

import fr.nivcoo.utilsz.config.text.TextMode;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TextFormat {
    TextMode value() default TextMode.AUTO;
}
