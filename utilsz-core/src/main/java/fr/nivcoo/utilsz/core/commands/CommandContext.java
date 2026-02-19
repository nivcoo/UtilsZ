package fr.nivcoo.utilsz.core.commands;

public record CommandContext(
        Sender sender,
        String label,
        String[] args
) {}
