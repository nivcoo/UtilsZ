package fr.nivcoo.utilsz.platform.velocity.commands;

import fr.nivcoo.utilsz.core.commands.Command;
import fr.nivcoo.utilsz.core.commands.CommandContext;

import java.util.List;

public interface VelocityCommand extends Command {

    void execute(VelocityCommandContext ctx);

    List<String> tabComplete(VelocityCommandContext ctx);

    @Override
    default void execute(CommandContext ctx) {
        execute(new VelocityCommandContext(ctx));
    }

    @Override
    default List<String> tabComplete(CommandContext ctx) {
        return tabComplete(new VelocityCommandContext(ctx));
    }
}