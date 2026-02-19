package fr.nivcoo.utilsz.core.config.spi;


import fr.nivcoo.utilsz.core.config.annotations.Converter;

import java.util.Map;
import java.util.function.Supplier;

public interface ConverterProvider {
    Map<Class<?>, Supplier<Converter<?>>> converters();
    default int priority() { return 0; }
}
