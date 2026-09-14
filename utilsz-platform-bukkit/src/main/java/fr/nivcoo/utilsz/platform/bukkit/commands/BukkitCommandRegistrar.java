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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public record BukkitCommandRegistrar(JavaPlugin plugin, boolean overrideExisting) implements CommandRegistrar {

    public BukkitCommandRegistrar(JavaPlugin plugin) {
        this(plugin, false);
    }

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
            registerDynamic(rootLabel, overrideExisting ? List.of() : rootAliases, dispatcher);
            if (overrideExisting) {
                for (String alias : rootAliases) {
                    registerDynamic(alias, List.of(), dispatcher);
                }
            }
            return;
        }
        List<String> declaredAliases = declaredAliases(pc);
        requireDeclaredAliases(rootLabel, rootAliases, declaredAliases);

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

        if (overrideExisting) {
            LinkedHashSet<String> labels = new LinkedHashSet<>();
            labels.add(rootLabel);
            labels.addAll(declaredAliases);
            for (String label : labels) {
                registerPreferred(label, pc);
            }
        }
    }

    private List<String> declaredAliases(PluginCommand command) {
        Object aliases = plugin.getDescription().getCommands()
                .getOrDefault(command.getName(), Map.of()).get("aliases");
        if (aliases instanceof List<?> values) {
            return values.stream().map(Object::toString).toList();
        }
        return aliases == null ? List.of() : List.of(aliases.toString());
    }

    private static void requireDeclaredAliases(
            String rootLabel,
            List<String> rootAliases,
            List<String> declaredAliases
    ) {
        for (String alias : rootAliases) {
            boolean declared = declaredAliases.stream()
                    .anyMatch(candidate -> candidate.equalsIgnoreCase(alias));
            if (!declared) {
                throw new IllegalStateException(
                        "Root alias '" + alias + "' for command '" + rootLabel
                                + "' must be declared in plugin.yml"
                );
            }
        }
    }

    private void registerPreferred(String label, PluginCommand command) {
        plugin.registerCommand(label, command.getDescription(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                command.execute(source.getSender(), label, args);
            }

            @Override
            public List<String> suggest(CommandSourceStack source, String[] args) {
                return canUse(source.getSender())
                        ? command.tabComplete(source.getSender(), label, args) : List.of();
            }

            @Override
            public boolean canUse(CommandSender sender) {
                return plugin.isEnabled() && command.testPermissionSilent(sender);
            }

            @Override
            public String permission() {
                return command.getPermission();
            }
        });
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
