package fr.nivcoo.utilsz.core.commands;

import java.util.List;

public interface Command {
    List<String> getAliases();
    String getPermission();
    String getUsage();
    default String getUsage(CommandContext ctx) { return getUsage(); }
    String getDescription();
    int getMinArgs();
    int getMaxArgs();
    boolean canBeExecutedByConsole();

    void execute(CommandContext ctx);

    List<String> tabComplete(CommandContext ctx);
}
