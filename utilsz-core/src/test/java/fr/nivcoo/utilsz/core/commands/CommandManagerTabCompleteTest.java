package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandManagerTabCompleteTest {

    private static final CommandsConfigProvider MESSAGES = new SimpleCommandsConfig(
            Component.empty(),
            Component.empty(),
            List.of()
    );
    private static final Sender SENDER = new Sender() {
        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public boolean isConsole() {
            return false;
        }

        @Override
        public void sendMessage(Component component) {
        }
    };

    @Test
    void keepsCompletingRootAliasesWhenPrefixIsAlsoAnExactAlias() {
        CommandManager manager = manager();
        manager.addCommand(new TestCommand("tp", List.of("villager-id")));
        manager.addCommand(new TestCommand("tphere", List.of("villager-id")));

        assertEquals(List.of("tp", "tphere"), manager.tabComplete(SENDER, "tradegui", new String[]{"tp"}));
    }

    @Test
    void delegatesToExactSubcommandAfterTheAliasIsCompleted() {
        CommandManager manager = manager();
        manager.addCommand(new TestCommand("tp", List.of("1", "2")));
        manager.addCommand(new TestCommand("tphere", List.of("3")));

        assertEquals(List.of("1", "2"), manager.tabComplete(SENDER, "tradegui", new String[]{"tp", ""}));
    }

    @Test
    void matchesRootAliasesFromTheStart() {
        CommandManager manager = manager();
        manager.addCommand(new TestCommand("create", List.of()));
        manager.addCommand(new TestCommand("recreate", List.of()));

        assertEquals(List.of("create"), manager.tabComplete(SENDER, "tradegui", new String[]{"cre"}));
    }

    private static CommandManager manager() {
        return new CommandManager((rootLabel, dispatcher) -> { }, MESSAGES, "tradegui", "");
    }

    private record TestCommand(String alias, List<String> suggestions) implements Command {
        @Override
        public List<String> getAliases() {
            return List.of(alias);
        }

        @Override
        public String getPermission() {
            return "";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public int getMinArgs() {
            return 1;
        }

        @Override
        public int getMaxArgs() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canBeExecutedByConsole() {
            return true;
        }

        @Override
        public void execute(CommandContext ctx) {
        }

        @Override
        public List<String> tabComplete(CommandContext ctx) {
            return suggestions;
        }
    }
}
