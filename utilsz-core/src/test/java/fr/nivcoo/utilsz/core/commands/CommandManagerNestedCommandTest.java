package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandManagerNestedCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final CommandsConfigProvider MESSAGES = new SimpleCommandsConfig(
            Component.text("denied"),
            Component.text("Usage: {0}"),
            List.of()
    );

    @Test
    void dispatchesTheMostSpecificRouteWithLocalArguments() {
        CommandManager manager = manager();
        List<String[]> calls = new ArrayList<>();
        boolean[] parentCalled = {false};
        manager.addCommand(command(List.of("admin"), "", "", 1, Integer.MAX_VALUE,
                ctx -> parentCalled[0] = true, ctx -> List.of()));
        CommandSection admin = manager.addSection("admin");
        admin.addCommand(command(List.of("reload", "rl"), "admin.reload", "", 1, 1,
                ctx -> calls.add(ctx.args()), ctx -> List.of()));
        admin.addSection("logs").addCommand(command(
                List.of("purge"), "admin.logs.purge", "", 2, 3,
                ctx -> calls.add(ctx.args()), ctx -> List.of()));
        TestSender sender = new TestSender("admin.reload", "admin.logs.purge");

        manager.dispatch(sender, "auction", new String[]{"admin", "rl"});
        manager.dispatch(sender, "auction", new String[]{"ADMIN", "LOGS", "PURGE", "30"});

        assertFalse(parentCalled[0]);
        assertEquals(2, calls.size());
        assertArrayEquals(new String[]{"rl"}, calls.get(0));
        assertArrayEquals(new String[]{"PURGE", "30"}, calls.get(1));
    }

    @Test
    void fallsBackToTheParentCommandWhenNoNestedRouteMatches() {
        CommandManager manager = manager();
        List<String[]> calls = new ArrayList<>();
        manager.addCommand(command(List.of("admin"), "", "", 1, Integer.MAX_VALUE,
                ctx -> calls.add(ctx.args()), ctx -> List.of()));
        manager.addSection("admin").addCommand(command(
                List.of("reload"), "", "", 1, 1,
                ctx -> calls.add(ctx.args()), ctx -> List.of()));

        manager.dispatch(new TestSender(), "auction", new String[]{"admin", "unknown"});

        assertEquals(1, calls.size());
        assertArrayEquals(new String[]{"admin", "unknown"}, calls.getFirst());
    }

    @Test
    void checksThePermissionOfTheSelectedNestedCommand() {
        CommandManager manager = manager();
        boolean[] called = {false};
        manager.addSection("admin").addCommand(command(
                List.of("reload"), "admin.reload", "", 1, 1,
                ctx -> called[0] = true, ctx -> List.of()));
        TestSender sender = new TestSender();

        manager.dispatch(sender, "auction", new String[]{"admin", "reload"});

        assertFalse(called[0]);
        assertEquals(List.of("denied"), sender.messages());
    }

    @Test
    void sendsTheConfiguredPlayerOnlyMessage() {
        CommandsConfigProvider messages = new SimpleCommandsConfig(
                Component.text("denied"),
                Component.text("Usage: {0}"),
                Component.text("players only"),
                List.of()
        );
        CommandManager manager = manager(messages);
        manager.addSection("admin").addCommand(playerCommand(List.of("open"), "", "", 1, 1));
        TestSender sender = new TestSender(true);

        manager.dispatch(sender, "auction", new String[]{"admin", "open"});

        assertEquals(List.of("players only"), sender.messages());
    }

    @Test
    void expandsRelativeUsageWithTheCompleteNestedRoute() {
        CommandManager manager = manager();
        manager.addSection("admin").addSection("logs").addCommand(command(
                List.of("purge"), "", "<days> [player]", 2, 3,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender();

        manager.dispatch(sender, "auction", new String[]{"admin", "logs", "purge"});

        assertEquals(List.of("Usage: auction admin logs purge <days> [player]"), sender.messages());
    }

    @Test
    void completesNestedRoutesByPermissionAndDelegatesLocalArguments() {
        CommandManager manager = manager();
        List<String[]> completionCalls = new ArrayList<>();
        CommandSection admin = manager.addSection("admin");
        admin.addCommand(command(List.of("reload", "rl"), "admin.reload", "", 1, 1,
                ctx -> { }, ctx -> {
                    completionCalls.add(ctx.args());
                    return List.of("now");
                }));
        CommandSection logs = admin.addSection("logs");
        logs.addCommand(command(List.of("purge"), "admin.logs.purge", "", 1, 3,
                ctx -> { }, ctx -> List.of()));
        logs.addCommand(command(List.of("player"), "admin.logs.player", "", 1, 2,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender("admin.reload", "admin.logs.player");

        assertEquals(List.of("admin"), manager.tabComplete(sender, "auction", new String[]{""}));
        assertEquals(List.of("reload", "rl", "logs"),
                manager.tabComplete(sender, "auction", new String[]{"admin", ""}));
        assertEquals(List.of("player"),
                manager.tabComplete(sender, "auction", new String[]{"admin", "logs", ""}));
        assertEquals(List.of("now"),
                manager.tabComplete(sender, "auction", new String[]{"admin", "reload", ""}));
        assertEquals(1, completionCalls.size());
        assertArrayEquals(new String[]{"reload", ""}, completionCalls.getFirst());
    }

    @Test
    void dispatchesNestedSectionAliasesWithLocalArguments() {
        CommandManager manager = manager();
        List<String[]> calls = new ArrayList<>();
        CommandSection admin = manager.addSection("admin", "a");
        CommandSection logs = admin.addSection("logs", "l", "journal");
        logs.addCommand(command(List.of("purge", "clear"), "", "", 2, 2,
                ctx -> calls.add(ctx.args()), ctx -> List.of()));

        manager.dispatch(new TestSender(), "auction", new String[]{"A", "JOURNAL", "CLEAR", "30"});

        assertEquals(1, calls.size());
        assertArrayEquals(new String[]{"CLEAR", "30"}, calls.getFirst());
    }

    @Test
    void completesEveryAliasOfNestedSections() {
        CommandManager manager = manager();
        CommandSection admin = manager.addSection("admin", "a");
        admin.addSection("logs", "l", "journal").addCommand(command(
                List.of("purge", "clear"), "", "", 1, 2,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender();

        assertEquals(List.of("admin", "a"),
                manager.tabComplete(sender, "auction", new String[]{""}));
        assertEquals(List.of("logs", "l", "journal"),
                manager.tabComplete(sender, "auction", new String[]{"a", ""}));
        assertEquals(List.of("journal"),
                manager.tabComplete(sender, "auction", new String[]{"a", "j"}));
        assertEquals(List.of("purge", "clear"),
                manager.tabComplete(sender, "auction", new String[]{"a", "journal", ""}));
    }

    @Test
    void sectionAliasesAlwaysProduceCanonicalUsage() {
        CommandManager manager = manager();
        CommandSection admin = manager.addSection("admin", "a");
        CommandSection logs = admin.addSection("logs", "l");
        TestCommand purge = command(List.of("purge", "clear"), "", "<days>", 2, 2,
                ctx -> { }, ctx -> List.of());
        logs.addCommand(purge);
        TestSender sender = new TestSender();

        manager.dispatch(sender, "ah", new String[]{"a", "l", "clear"});

        assertEquals(List.of("Usage: auction admin logs purge <days>"), sender.messages());
        assertEquals("auction admin logs purge <days>", manager.getUsage(purge,
                new CommandContext(sender, "ah", new String[]{"clear"})));
    }

    @Test
    void rejectsOverlappingAliasesBetweenSiblingSections() {
        CommandSection admin = manager().addSection("admin");
        admin.addSection("logs", "l", "journal");

        assertThrows(IllegalArgumentException.class,
                () -> admin.addSection("ledger", "JOURNAL"));
    }

    @Test
    void rejectsOverlappingLeafAliasesAtTheSameLevel() {
        CommandSection admin = manager().addSection("admin");
        admin.addCommand(command(List.of("reload", "rl"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of()));

        assertThrows(IllegalArgumentException.class, () -> admin.addCommand(command(
                List.of("refresh", "RL"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of())));
    }

    @Test
    void rejectsDifferentAliasesForAnExecutableSectionNode() {
        CommandManager sectionFirst = manager();
        CommandSection sectionFirstAdmin = sectionFirst.addSection("admin");
        sectionFirstAdmin.addSection("logs", "l");

        assertThrows(IllegalArgumentException.class, () -> sectionFirstAdmin.addCommand(command(
                List.of("list", "l"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of())));

        CommandManager commandFirst = manager();
        CommandSection commandFirstAdmin = commandFirst.addSection("admin");
        commandFirstAdmin.addCommand(command(List.of("logs"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> commandFirstAdmin.addSection("logs", "l"));
    }

    @Test
    void completesAnExecutableNodeAndItsChildrenTogether() {
        CommandManager manager = manager();
        manager.addCommand(command(List.of("admin"), "", "", 1, Integer.MAX_VALUE,
                ctx -> { }, ctx -> List.of("argument")));
        manager.addSection("admin").addCommand(command(
                List.of("reload"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of()));

        assertEquals(List.of("reload", "argument"),
                manager.tabComplete(new TestSender(), "auction", new String[]{"admin", ""}));
    }

    @Test
    void rejectsRegisteringTheSameCommandInstanceTwice() {
        CommandManager manager = manager();
        CommandSection admin = manager.addSection("admin");
        CommandSection staff = manager.addSection("staff");
        TestCommand reload = command(List.of("reload"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of());
        admin.addCommand(reload);

        assertThrows(IllegalArgumentException.class, () -> staff.addCommand(reload));
    }

    @Test
    void sectionsGenerateTheirUsageAndFallBackToTheDeepestBranch() {
        CommandManager manager = manager();
        CommandSection admin = manager.addSection("admin").permission("admin.section");
        admin.addCommand(command(List.of("reload"), "admin.reload", "", 1, 1,
                ctx -> { }, ctx -> List.of()));
        CommandSection option = admin.addSection("option").permission("admin.option");
        option.addCommand(command(List.of("set"), "admin.option", "<player>", 2, 2,
                ctx -> { }, ctx -> List.of()));
        option.addCommand(command(List.of("reset"), "admin.option", "<player>", 2, 2,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender("admin.section", "admin.reload", "admin.option");

        manager.dispatch(sender, "auction", new String[]{"admin"});
        manager.dispatch(sender, "auction", new String[]{"admin", "option", "unknown"});

        assertEquals(List.of(
                "Usage: auction admin <reload|option>",
                "Usage: auction admin option <set|reset>"
        ), sender.messages());
    }

    @Test
    void aLeafUsageFollowsTheSectionWhereItIsMounted() {
        TestCommand command = command(List.of("reload", "rl"), "", "[force]", 1, 2,
                ctx -> { }, ctx -> List.of());
        CommandManager adminManager = manager();
        adminManager.addSection("admin").addCommand(command);
        CommandManager staffManager = manager();
        staffManager.addSection("staff").addSection("tools").addCommand(command);
        TestSender adminSender = new TestSender();
        TestSender staffSender = new TestSender();

        adminManager.dispatch(adminSender, "auction", new String[]{"admin", "reload", "yes", "extra"});
        staffManager.dispatch(staffSender, "auction",
                new String[]{"staff", "tools", "rl", "yes", "extra"});

        assertEquals(List.of("Usage: auction admin reload [force]"), adminSender.messages());
        assertEquals(List.of("Usage: auction staff tools reload [force]"), staffSender.messages());
        assertEquals("auction admin reload [force]", adminManager.getUsage(command,
                new CommandContext(adminSender, "auction", new String[]{"reload"})));
        assertEquals("auction staff tools reload [force]", staffManager.getUsage(command,
                new CommandContext(staffSender, "auction", new String[]{"reload"})));
    }

    @Test
    void sectionPermissionAndConsolePolicyDoNotReplaceLeafRules() {
        CommandManager manager = manager(new SimpleCommandsConfig(
                Component.text("denied"), Component.text("Usage: {0}"),
                Component.text("players only"), List.of()));
        boolean[] called = {false};
        CommandSection admin = manager.addSection("admin")
                .permission("admin.section")
                .canBeExecutedByConsole(false);
        admin.addCommand(command(List.of("reload"), "admin.reload", "", 1, 1,
                ctx -> called[0] = true, ctx -> List.of()));
        TestSender player = new TestSender("admin.reload");
        TestSender console = new TestSender(true, "admin.section", "admin.reload");

        manager.dispatch(player, "auction", new String[]{"admin"});
        manager.dispatch(player, "auction", new String[]{"admin", "reload"});
        manager.dispatch(console, "auction", new String[]{"admin"});
        manager.dispatch(console, "auction", new String[]{"admin", "reload"});

        assertEquals(List.of("denied"), player.messages());
        assertEquals(List.of("players only"), console.messages());
        assertEquals(true, called[0]);
    }

    @Test
    void rootCommandsCanDeclareOnlyTheirArgumentUsage() {
        CommandManager manager = manager();
        manager.addCommand(command(List.of("sell"), "", "<price>", 2, 2,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender();

        manager.dispatch(sender, "auction", new String[]{"sell"});

        assertEquals(List.of("Usage: auction sell <price>"), sender.messages());
    }

    @Test
    void rejectsInvalidSectionNames() {
        CommandManager manager = manager();

        assertThrows(IllegalArgumentException.class, () -> manager.addSection(" "));
        assertThrows(IllegalArgumentException.class, () -> manager.addSection("admin tools"));
    }

    @Test
    void checksTheDefaultCommandPermissionAndConsolePolicy() {
        CommandsConfigProvider messages = new SimpleCommandsConfig(
                Component.text("denied"), Component.text("Usage: {0}"),
                Component.text("players only"), List.of());
        Command defaultCommand = playerCommand(List.of(""), "auction.open", "", 0, 0);
        CommandManager manager = new CommandManager((rootLabel, dispatcher) -> { }, messages,
                "auction", "auction.root", defaultCommand);
        TestSender player = new TestSender("auction.root");
        TestSender console = new TestSender(true, "auction.root", "auction.open");

        manager.dispatch(player, "auction", new String[0]);
        manager.dispatch(console, "auction", new String[0]);

        assertEquals(List.of("denied"), player.messages());
        assertEquals(List.of("players only"), console.messages());
    }

    private static CommandManager manager() {
        return manager(MESSAGES);
    }

    private static CommandManager manager(CommandsConfigProvider messages) {
        return new CommandManager((rootLabel, dispatcher) -> { }, messages, "auction", "");
    }

    private static TestCommand command(
            List<String> aliases,
            String permission,
            String usage,
            int minArgs,
            int maxArgs,
            Consumer<CommandContext> execute,
            Function<CommandContext, List<String>> tabComplete
    ) {
        return new TestCommand(aliases, permission, usage, minArgs, maxArgs, true, execute, tabComplete);
    }

    private static TestCommand playerCommand(
            List<String> aliases,
            String permission,
            String usage,
            int minArgs,
            int maxArgs
    ) {
        return new TestCommand(aliases, permission, usage, minArgs, maxArgs, false, ctx -> { }, ctx -> List.of());
    }

    private record TestCommand(
            List<String> aliases,
            String permission,
            String usage,
            int minArgs,
            int maxArgs,
            boolean console,
            Consumer<CommandContext> execution,
            Function<CommandContext, List<String>> completion
    ) implements Command {
        @Override
        public List<String> getAliases() {
            return aliases;
        }

        @Override
        public String getPermission() {
            return permission;
        }

        @Override
        public String getUsage() {
            return usage;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public int getMinArgs() {
            return minArgs;
        }

        @Override
        public int getMaxArgs() {
            return maxArgs;
        }

        @Override
        public boolean canBeExecutedByConsole() {
            return console;
        }

        @Override
        public void execute(CommandContext ctx) {
            execution.accept(ctx);
        }

        @Override
        public List<String> tabComplete(CommandContext ctx) {
            return completion.apply(ctx);
        }
    }

    private static final class TestSender implements Sender {
        private final Set<String> permissions;
        private final List<String> messages = new ArrayList<>();
        private final boolean console;

        private TestSender(String... permissions) {
            this(false, permissions);
        }

        private TestSender(boolean console, String... permissions) {
            this.console = console;
            this.permissions = new HashSet<>(Arrays.asList(permissions));
        }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        @Override
        public boolean isConsole() {
            return console;
        }

        @Override
        public void sendMessage(Component component) {
            messages.add(PLAIN.serialize(component));
        }

        private List<String> messages() {
            return messages;
        }
    }
}
