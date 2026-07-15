package fr.nivcoo.utilsz.platform.velocity.commands;

import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.nivcoo.utilsz.core.commands.CommandDispatcher;
import fr.nivcoo.utilsz.core.commands.CommandRegistrar;

import java.util.List;

@SuppressWarnings("unused")
public final class VelocityCommandRegistrar implements CommandRegistrar {

    private final ProxyServer proxy;

    public VelocityCommandRegistrar(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public void registerRoot(String rootLabel, CommandDispatcher dispatcher) {
        registerRoot(rootLabel, List.of(), dispatcher);
    }

    @Override
    public void registerRoot(String rootLabel, List<String> rootAliases, CommandDispatcher dispatcher) {
        SimpleCommand simple = new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                dispatcher.dispatch(
                        new VelocitySender(invocation.source()),
                        invocation.alias(),
                        invocation.arguments()
                );
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return dispatcher.tabComplete(
                        new VelocitySender(invocation.source()),
                        invocation.alias(),
                        invocation.arguments()
                );
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return true;
            }
        };

        CommandMeta.Builder meta = proxy.getCommandManager().metaBuilder(rootLabel);
        if (!rootAliases.isEmpty()) meta.aliases(rootAliases.toArray(String[]::new));

        proxy.getCommandManager().register(meta.build(), simple);
    }
}
