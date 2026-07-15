package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CommandManagerRootAliasTest {

    private static final CommandsConfigProvider MESSAGES = new SimpleCommandsConfig(
            Component.empty(),
            Component.empty(),
            List.of()
    );

    @Test
    void passesRootAliasesToAliasAwareRegistrar() {
        AliasAwareRegistrar registrar = new AliasAwareRegistrar();

        CommandManager manager = new CommandManager(
                registrar,
                MESSAGES,
                "lobby",
                List.of("hub"),
                ""
        );

        assertEquals("lobby", registrar.rootLabel);
        assertEquals(List.of("hub"), registrar.rootAliases);
        assertSame(manager, registrar.dispatcher);
    }

    @Test
    void legacyRegistrarFallsBackToRegisteringEveryAliasAsARoot() {
        LegacyRegistrar registrar = new LegacyRegistrar();

        CommandManager manager = new CommandManager(
                registrar,
                MESSAGES,
                "lobby",
                List.of("hub", "spawn"),
                ""
        );

        assertEquals(List.of("lobby", "hub", "spawn"), registrar.rootLabels);
        assertEquals(List.of(manager, manager, manager), registrar.dispatchers);
    }

    private static final class AliasAwareRegistrar implements CommandRegistrar {
        private String rootLabel;
        private List<String> rootAliases;
        private CommandDispatcher dispatcher;

        @Override
        public void registerRoot(String rootLabel, CommandDispatcher dispatcher) {
            throw new AssertionError("The alias-aware overload should be used");
        }

        @Override
        public void registerRoot(String rootLabel, List<String> rootAliases, CommandDispatcher dispatcher) {
            this.rootLabel = rootLabel;
            this.rootAliases = rootAliases;
            this.dispatcher = dispatcher;
        }
    }

    private static final class LegacyRegistrar implements CommandRegistrar {
        private final List<String> rootLabels = new ArrayList<>();
        private final List<CommandDispatcher> dispatchers = new ArrayList<>();

        @Override
        public void registerRoot(String rootLabel, CommandDispatcher dispatcher) {
            rootLabels.add(rootLabel);
            dispatchers.add(dispatcher);
        }
    }
}
