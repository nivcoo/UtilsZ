package fr.nivcoo.utilsz.runtime;

public final class RuntimeInitializer {

    static {
        Runtime.setPlatform(PlatformLoader.detect());
    }

    private RuntimeInitializer() {}
}
