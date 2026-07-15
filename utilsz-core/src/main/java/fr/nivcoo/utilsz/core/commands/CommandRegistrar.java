package fr.nivcoo.utilsz.core.commands;

import java.util.List;

public interface CommandRegistrar {
    void registerRoot(String rootLabel, CommandDispatcher dispatcher);

    default void registerRoot(String rootLabel, List<String> rootAliases, CommandDispatcher dispatcher) {
        registerRoot(rootLabel, dispatcher);
        for (String alias : rootAliases) registerRoot(alias, dispatcher);
    }
}
