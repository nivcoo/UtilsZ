package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;

public interface Sender {
    boolean hasPermission(String permission);
    boolean isConsole();
    void sendMessage(Component component);
}
