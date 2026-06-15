package fr.nivcoo.utilsz.core.commands;

public record CommandContext(Sender sender, String label, String[] args) {

    public <T extends Sender> T senderAs(Class<T> type) {
        return type.cast(sender);
    }
}