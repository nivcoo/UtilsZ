package fr.nivcoo.utilsz.platform.bukkit.commands;

import fr.nivcoo.utilsz.core.commands.CommandDispatcher;
import fr.nivcoo.utilsz.core.commands.CommandRegistrar;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.List;

@SuppressWarnings("unused")
public record BukkitCommandRegistrar(JavaPlugin plugin) implements CommandRegistrar {

    @Override
    public void registerRoot(String rootLabel, CommandDispatcher dispatcher) {
        registerRoot(rootLabel, List.of(), dispatcher);
    }

    @Override
    public void registerRoot(String rootLabel, List<String> rootAliases, CommandDispatcher dispatcher) {
        PluginCommand pc;
        try {
            pc = plugin.getCommand(rootLabel);
        } catch (UnsupportedOperationException ignored) {
            pc = null;
        }

        if (pc == null) {
            registerDynamic(rootLabel, rootAliases, dispatcher);
            return;
        }
        requireDeclaredAliases(rootLabel, rootAliases, pc);

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

    private static void requireDeclaredAliases(
            String rootLabel,
            List<String> rootAliases,
            PluginCommand command
    ) {
        for (String alias : rootAliases) {
            boolean declared = command.getAliases().stream()
                    .anyMatch(candidate -> candidate.equalsIgnoreCase(alias));
            if (!declared) {
                throw new IllegalStateException(
                        "Root alias '" + alias + "' for command '" + rootLabel
                                + "' must be declared in plugin.yml"
                );
            }
        }
    }

    private void registerDynamic(String rootLabel, List<String> rootAliases, CommandDispatcher dispatcher) {
        plugin.registerCommand(rootLabel, rootAliases, new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                dispatcher.dispatch(new BukkitSender(commandSourceStack.getSender()), rootLabel, args);
            }

            @Override
            public List<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return dispatcher.tabComplete(new BukkitSender(commandSourceStack.getSender()), rootLabel, args);
            }
        });
    }
}
