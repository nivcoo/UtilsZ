package fr.nivcoo.utilsz.platform.bukkit.commands;

import fr.nivcoo.utilsz.core.commands.Command;
import fr.nivcoo.utilsz.core.commands.CommandContext;

import java.util.List;

public interface BukkitCommand extends Command {

    void execute(BukkitCommandContext ctx);

    List<String> tabComplete(BukkitCommandContext ctx);

    @Override
    default void execute(CommandContext ctx) {
        execute(new BukkitCommandContext(ctx));
    }

    @Override
    default List<String> tabComplete(CommandContext ctx) {
        return tabComplete(new BukkitCommandContext(ctx));
    }
}