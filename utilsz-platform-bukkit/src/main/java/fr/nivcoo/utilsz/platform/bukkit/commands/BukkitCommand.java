package fr.nivcoo.utilsz.platform.bukkit.commands;

import fr.nivcoo.utilsz.core.commands.Command;
import fr.nivcoo.utilsz.core.commands.CommandContext;
import org.bukkit.command.CommandSender;

import java.util.List;

public interface BukkitCommand extends Command {

    default String getUsage(CommandSender sender) {
        return getUsage();
    }

    void execute(CommandSender sender, String label, String[] args);

    List<String> tabComplete(CommandSender sender, String label, String[] args);

    @Override
    default void execute(CommandContext ctx) {
        BukkitSender sender = ctx.senderAs(BukkitSender.class);
        execute(sender.sender(), ctx.label(), ctx.args());
    }

    @Override
    default String getUsage(CommandContext ctx) {
        BukkitSender sender = ctx.senderAs(BukkitSender.class);
        return getUsage(sender.sender());
    }

    @Override
    default List<String> tabComplete(CommandContext ctx) {
        BukkitSender sender = ctx.senderAs(BukkitSender.class);
        return tabComplete(sender.sender(), ctx.label(), ctx.args());
    }
}
