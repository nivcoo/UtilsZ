package fr.nivcoo.utilsz.platform.bukkit.commands;

import fr.nivcoo.utilsz.core.commands.Sender;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public record BukkitSender(CommandSender handle) implements Sender {

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
        return handle.getName();
    }

    @Override
    public UUID uuid() {
        return handle instanceof Player player ? player.getUniqueId() : null;
    }

    public CommandSender sender() {
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