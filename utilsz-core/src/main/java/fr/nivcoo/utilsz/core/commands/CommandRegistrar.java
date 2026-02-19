package fr.nivcoo.utilsz.core.commands;

public interface CommandRegistrar {
    void registerRoot(String rootLabel, CommandDispatcher dispatcher);
}
