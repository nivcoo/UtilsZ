package fr.nivcoo.utilsz.core.config.reload;

@FunctionalInterface
public interface ConfigReloadListener<T> {

    void reload(ConfigReloadEvent<T> event) throws Exception;
}
