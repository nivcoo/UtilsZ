package fr.nivcoo.utilsz.platform.bukkit.commands;

import fr.nivcoo.utilsz.core.commands.CommandContext;

public final class BukkitCommandContext {

    private final CommandContext context;
    private final BukkitSender sender;

    public BukkitCommandContext(CommandContext context) {
        this.context = context;
        this.sender = context.senderAs(BukkitSender.class);
    }

    public CommandContext core() {
        return context;
    }

    public BukkitSender sender() {
        return sender;
    }

    public String label() {
        return context.label();
    }

    public String[] args() {
        return context.args();
    }
}