package fr.nivcoo.utilsz.platform.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import fr.nivcoo.utilsz.core.commands.Sender;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public record VelocitySender(CommandSource handle) implements Sender {

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) return true;
        return handle.hasPermission(permission);
    }

    @Override
    public boolean isConsole() {
        return !(handle instanceof Player);
    }

    @Override
    public String name() {
        return handle instanceof Player player ? player.getUsername() : "Console";
    }

    @Override
    public UUID uuid() {
        return handle instanceof Player player ? player.getUniqueId() : null;
    }

    public CommandSource sender() {
        return handle;
    }

    public Player player() {
        return handle instanceof Player player ? player : null;
    }

    @Override
    public void sendMessage(Component component) {
        handle.sendMessage(component);
    }
}