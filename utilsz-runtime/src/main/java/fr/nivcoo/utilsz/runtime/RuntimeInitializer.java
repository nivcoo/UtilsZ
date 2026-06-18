package fr.nivcoo.utilsz.runtime;

import fr.nivcoo.utilsz.core.module.UtilsZModules;

public final class RuntimeInitializer {

    static {
        UtilsZModules.load();
    }

    private RuntimeInitializer() {}
}
