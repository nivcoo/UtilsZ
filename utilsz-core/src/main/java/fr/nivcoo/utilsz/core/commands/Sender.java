package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;

import java.util.UUID;

public interface Sender {

    boolean hasPermission(String permission);

    boolean isConsole();

    default boolean isPlayer() {
        return !isConsole();
    }

    String name();

    UUID uuid();

    void sendMessage(Component component);
}