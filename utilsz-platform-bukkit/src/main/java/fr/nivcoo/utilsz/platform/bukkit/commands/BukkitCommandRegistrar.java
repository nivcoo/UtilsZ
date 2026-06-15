package fr.nivcoo.utilsz.platform.bukkit.commands;

import fr.nivcoo.utilsz.core.commands.CommandDispatcher;
import fr.nivcoo.utilsz.core.commands.CommandRegistrar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record BukkitCommandRegistrar(JavaPlugin plugin) implements CommandRegistrar {

    @Override
    public void registerRoot(String rootLabel, CommandDispatcher dispatcher) {
        PluginCommand pc = plugin.getCommand(rootLabel);
        if (pc == null) throw new IllegalStateException("Command not found in plugin.yml: " + rootLabel);

        TabExecutor exec = new TabExecutor() {
            @Override
            public boolean onCommand(@NonNull CommandSender commandSender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
                return dispatcher.dispatch(new BukkitSender(commandSender), label, args);
            }

            @Override
            public List<String> onTabComplete(@NonNull CommandSender commandSender, @NonNull Command cmd, @NonNull String alias, String @NonNull [] args) {
                return dispatcher.tabComplete(new BukkitSender(commandSender), alias, args);
            }
        };

        pc.setExecutor(exec);
        pc.setTabCompleter(exec);
    }
}