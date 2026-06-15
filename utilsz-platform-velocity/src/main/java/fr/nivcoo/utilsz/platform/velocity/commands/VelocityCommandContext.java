package fr.nivcoo.utilsz.platform.velocity.commands;

import fr.nivcoo.utilsz.core.commands.CommandContext;

public final class VelocityCommandContext {

    private final CommandContext context;
    private final VelocitySender sender;

    public VelocityCommandContext(CommandContext context) {
        this.context = context;
        this.sender = context.senderAs(VelocitySender.class);
    }

    public CommandContext core() {
        return context;
    }

    public VelocitySender sender() {
        return sender;
    }

    public String label() {
        return context.label();
    }

    public String[] args() {
        return context.args();
    }
}