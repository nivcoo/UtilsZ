package fr.nivcoo.utilsz.platform.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import fr.nivcoo.utilsz.core.commands.Sender;
import net.kyori.adventure.text.Component;

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
    public void sendMessage(Component component) {
        handle.sendMessage(component);
    }
}
