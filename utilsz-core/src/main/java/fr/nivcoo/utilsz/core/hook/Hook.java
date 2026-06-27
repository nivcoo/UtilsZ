package fr.nivcoo.utilsz.core.hook;

public interface Hook<C extends HookContext> {
    String id();

    default boolean enabled(C context) {
        return true;
    }

    default String requiredPlugin() {
        return null;
    }

    void load(C context);
}
