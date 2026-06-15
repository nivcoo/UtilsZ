package fr.nivcoo.utilsz.platform.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import fr.nivcoo.utilsz.core.commands.Command;
import fr.nivcoo.utilsz.core.commands.CommandContext;

import java.util.List;

public interface VelocityCommand extends Command {

    void execute(CommandSource source, String alias, String[] args);

    List<String> tabComplete(CommandSource source, String alias, String[] args);

    @Override
    default void execute(CommandContext ctx) {
        VelocitySender sender = ctx.senderAs(VelocitySender.class);
        execute(sender.sender(), ctx.label(), ctx.args());
    }

    @Override
    default List<String> tabComplete(CommandContext ctx) {
        VelocitySender sender = ctx.senderAs(VelocitySender.class);
        return tabComplete(sender.sender(), ctx.label(), ctx.args());
    }
}