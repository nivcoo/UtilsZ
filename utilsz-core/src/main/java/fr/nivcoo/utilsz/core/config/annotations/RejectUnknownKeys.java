package fr.nivcoo.utilsz.core.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects YAML keys that are not represented by the configuration object
 * graph. Dynamic {@code Map<String, Object>} and {@code Object} values remain
 * intentionally open.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RejectUnknownKeys {
}
