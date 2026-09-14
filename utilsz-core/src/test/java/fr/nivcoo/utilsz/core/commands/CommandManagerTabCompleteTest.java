package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

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

    @Test
    void forwardsAllDefaultArgumentsAndTheInvokedRootAlias() {
        CommandManager manager = manager();
        manager.setDefaultCommand(new TestCommand("gamemode", context ->
                List.of(context.label(), String.join(" ", context.args()))));

        assertEquals(List.of("gm", "creative Ni"),
                manager.tabComplete(SENDER, "gm", new String[]{"creative", "Ni"}));
        assertEquals(List.of("gm", "creative Nico "),
                manager.tabComplete(SENDER, "gm", new String[]{"creative", "Nico", ""}));
    }

    @Test
    void keepsDefaultSuggestionsOutOfSubcommandsAndSections() {
        CommandManager manager = manager();
        manager.setDefaultCommand(new TestCommand("open", List.of("player")));
        manager.addCommand(new TestCommand("tp", List.of("destination")));
        manager.addSection("admin", "a").addCommand(new TestCommand("reload", List.of("now")));

        assertEquals(List.of("tp", "admin", "a", "player"),
                manager.tabComplete(SENDER, "tradegui", new String[]{""}));
        assertEquals(List.of("destination"),
                manager.tabComplete(SENDER, "tradegui", new String[]{"tp", ""}));
        assertEquals(List.of("reload"),
                manager.tabComplete(SENDER, "tradegui", new String[]{"a", ""}));
        assertEquals(List.of("now"),
                manager.tabComplete(SENDER, "tradegui", new String[]{"a", "reload", ""}));
        assertEquals(List.of(),
                manager.tabComplete(SENDER, "tradegui", new String[]{"admin", "unknown"}));
        assertEquals(List.of("player"),
                manager.tabComplete(SENDER, "tradegui", new String[]{"unrouted", ""}));
    }

    private static CommandManager manager() {
        return new CommandManager((rootLabel, dispatcher) -> { }, MESSAGES, "tradegui", "");
    }

    private record TestCommand(String alias, Function<CommandContext, List<String>> completion) implements Command {
        private TestCommand(String alias, List<String> suggestions) {
            this(alias, context -> suggestions);
        }
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
            return completion.apply(ctx);
        }
    }
}
