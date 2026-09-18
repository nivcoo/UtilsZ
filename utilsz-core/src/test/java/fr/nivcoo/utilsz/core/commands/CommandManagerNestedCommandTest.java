package fr.nivcoo.utilsz.core.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        manager.setExecutionGuard((command, context) -> {
            throw new AssertionError("Tab completion must not execute the guard");
        });
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
    void usagePreservesInvokedRootSectionAndLeafAliases() {
        CommandManager manager = manager();
        CommandSection admin = manager.addSection("admin", "a");
        CommandSection logs = admin.addSection("logs", "l");
        TestCommand purge = command(List.of("purge", "clear"), "", "<days>", 2, 2,
                ctx -> { }, ctx -> List.of());
        logs.addCommand(purge);
        TestSender sender = new TestSender();

        manager.dispatch(sender, "ah", new String[]{"a", "l", "clear"});
        manager.dispatch(sender, "otherplugin:ah", new String[]{"a", "l", "clear"});
        manager.dispatch(sender, "otherplugin:ah", new String[]{"a", "l", "unknown"});
        CommandContext context = new CommandContext(sender, "otherplugin:hdv", new String[]{"clear"});
        manager.sendUsage(purge, context);

        assertEquals(List.of(
                "Usage: ah a l clear <days>",
                "Usage: ah a l clear <days>",
                "Usage: ah a l <purge>",
                "Usage: hdv admin logs clear <days>"
        ), sender.messages());
        assertEquals("hdv admin logs clear <days>", manager.getUsage(purge, context));
    }

    @Test
    void defaultUsageOmitsNamespacesWithoutChangingTheCommandContext() {
        List<CommandContext> calls = new ArrayList<>();
        Command defaultCommand = command(List.of(""), "", "<target> [minecraft:overworld]", 1, 1,
                calls::add, ctx -> List.of());
        CommandManager manager = new CommandManager(
                (rootLabel, dispatcher) -> { }, MESSAGES,
                "tp", List.of("teleport"), "", defaultCommand);
        TestSender sender = new TestSender();

        manager.dispatch(sender, "edensync:tp", new String[0]);
        manager.dispatch(sender, "edensync:tp", new String[]{"one", "two"});
        manager.dispatch(sender, "edensync:tp", new String[]{"minecraft:stone"});
        CommandContext context = new CommandContext(sender, "minecraft:teleport", new String[0]);
        manager.sendUsage(defaultCommand, context);

        assertEquals(List.of(
                "Usage: tp <target> [minecraft:overworld]",
                "Usage: tp <target> [minecraft:overworld]",
                "Usage: teleport <target> [minecraft:overworld]"
        ), sender.messages());
        assertEquals("teleport <target> [minecraft:overworld]", manager.getUsage(defaultCommand, context));
        assertEquals(1, calls.size());
        assertEquals("edensync:tp", calls.getFirst().label());
        assertArrayEquals(new String[]{"minecraft:stone"}, calls.getFirst().args());
    }

    @Test
    void rootAndLeafAliasesProduceTheInvokedUsage() {
        CommandManager manager = new CommandManager(
                (rootLabel, dispatcher) -> { }, MESSAGES,
                "auction", List.of("ah", "hdv"), "");
        manager.addCommand(command(List.of("sell", "s"), "", "<price>", 2, 2,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender();

        manager.dispatch(sender, "hdv", new String[]{"sell"});
        manager.dispatch(sender, "ah", new String[]{"s"});

        assertEquals(List.of(
                "Usage: hdv sell <price>",
                "Usage: ah s <price>"
        ), sender.messages());
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
    void executableSectionNodeKeepsItsOwnUsageWhenArgumentsAreMissing() {
        CommandManager manager = manager();
        manager.addCommand(command(List.of("sell"), "", "<price>", 2, 2,
                ctx -> { }, ctx -> List.of()));
        manager.addSection("sell").addCommand(command(
                List.of("inventory"), "", "<price>", 2, 2,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender();

        manager.dispatch(sender, "auction", new String[]{"sell"});
        manager.dispatch(sender, "auction", new String[]{"sell", "inventory"});

        assertEquals(List.of(
                "Usage: auction sell <price>",
                "Usage: auction sell inventory <price>"
        ), sender.messages());
    }

    @Test
    void routeOnlyExecutableSectionStillShowsItsChildrenForAnUnknownToken() {
        CommandManager manager = manager();
        CommandSection admin = manager.addSection("admin");
        admin.addCommand(command(List.of("logs"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of()));
        admin.addSection("logs").addCommand(command(
                List.of("player"), "", "<player>", 2, 2,
                ctx -> { }, ctx -> List.of()));
        TestSender sender = new TestSender();

        manager.dispatch(sender, "auction", new String[]{"admin", "logs", "unknown"});

        assertEquals(List.of("Usage: auction admin logs <player>"), sender.messages());
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
    void snapshotsAliasesAndDoesNotExposeTheRegistrationList() {
        CommandManager manager = manager();
        ArrayList<String> aliases = new ArrayList<>(List.of("reload", "rl"));
        boolean[] called = {false};
        manager.addCommand(command(aliases, "", "", 1, 1,
                ctx -> called[0] = true, ctx -> List.of()));

        aliases.clear();
        aliases.add("changed");
        manager.dispatch(new TestSender(), "auction", new String[]{"reload"});

        assertTrue(called[0]);
        assertThrows(UnsupportedOperationException.class,
                () -> manager.getCommands().add(command(
                        List.of("other"), "", "", 1, 1,
                        ctx -> { }, ctx -> List.of())));
    }

    @Test
    void returnsTheRegisteredCanonicalSectionPath() {
        CommandManager manager = manager();
        manager.addSection("admin", "a");

        CommandSection section = manager.addSection("ADMIN", "A");

        assertEquals("admin", section.getPath());
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
        assertEquals(List.of("Usage: auction staff tools rl [force]"), staffSender.messages());
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

    @Test
    void preservesRichDefaultCommandUsageOnInvalidArguments() {
        Component prefix = Component.text("Usage: ", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(Component.text("Click to complete", NamedTextColor.GREEN))
                .clickEvent(ClickEvent.suggestCommand("/auction "));
        CommandsConfigProvider messages = new SimpleCommandsConfig(
                Component.empty(), prefix.append(Component.text("{0}", NamedTextColor.GOLD)), List.of());
        Command defaultCommand = command(List.of(""), "", "<red>", 1, 1,
                ctx -> { }, ctx -> List.of());
        CommandManager manager = new CommandManager(
                (rootLabel, dispatcher) -> { }, messages,
                "auction", "auction.root", defaultCommand);
        TestSender sender = new TestSender("auction.root");

        manager.dispatch(sender, "auction", new String[0]);
        manager.dispatch(sender, "auction", new String[]{"one", "two"});

        Component expected = prefix.append(Component.text("auction <red>", NamedTextColor.GOLD));
        assertEquals(List.of(expected.compact(), expected.compact()),
                sender.components.stream().map(Component::compact).toList());
        assertEquals(List.of("Usage: auction <red>", "Usage: auction <red>"), sender.messages());
    }

    @Test
    void doesNotLeakOrDuplicateDefaultSuggestions() {
        Command defaultCommand = command(List.of(""), "", "", 0, Integer.MAX_VALUE,
                ctx -> { }, ctx -> List.of("sell", "root-value"));
        CommandManager manager = new CommandManager(
                (rootLabel, dispatcher) -> { }, MESSAGES,
                "auction", "", defaultCommand);
        manager.addCommand(command(List.of("sell"), "", "", 1, 1,
                ctx -> { }, ctx -> List.of()));
        manager.addSection("admin");
        TestSender sender = new TestSender();

        assertEquals(List.of("sell", "root-value"),
                manager.tabComplete(sender, "auction", new String[]{""}));
        assertEquals(List.of(),
                manager.tabComplete(sender, "auction", new String[]{"admin", "unknown"}));
    }

    @Test
    void validatesTheLegacyEmptyRootFallback() {
        boolean[] called = {false};
        CommandManager manager = new CommandManager(
                (rootLabel, dispatcher) -> { }, MESSAGES,
                "auction", "auction.root", false);
        manager.addCommand(command(List.of("auction"), "auction.run", "<value>", 2, 2,
                ctx -> called[0] = true, ctx -> List.of()));
        TestSender denied = new TestSender("auction.root");
        TestSender invalidUsage = new TestSender("auction.root", "auction.run");

        manager.dispatch(denied, "auction", new String[0]);
        manager.dispatch(invalidUsage, "auction", new String[0]);

        assertFalse(called[0]);
        assertEquals(List.of("denied"), denied.messages());
        assertEquals(List.of("Usage: auction auction <value>"), invalidUsage.messages());
    }

    @Test
    void guardsEveryExecutionRouteAndCanBeCleared() {
        List<Command> guarded = new ArrayList<>();
        List<CommandContext> contexts = new ArrayList<>();
        List<CommandContext> executions = new ArrayList<>();
        Command defaultCommand = command(List.of("open"), "", "[player]", 0, 1,
                executions::add, ctx -> List.of());
        Command directCommand = command(List.of("clear", "ci"), "", "", 1, 1,
                executions::add, ctx -> List.of());
        Command nestedCommand = command(List.of("purge", "delete"), "", "<days>", 2, 2,
                executions::add, ctx -> List.of());
        CommandManager manager = new CommandManager(
                (rootLabel, dispatcher) -> { }, MESSAGES, "auction", "", defaultCommand);
        manager.addCommand(directCommand);
        manager.addSection("admin", "a").addCommand(nestedCommand);
        manager.setExecutionGuard((command, context) -> {
            guarded.add(command);
            contexts.add(context);
            return false;
        });
        TestSender sender = new TestSender();

        assertTrue(manager.dispatch(sender, "ah", new String[0]));
        assertTrue(manager.dispatch(sender, "ah", new String[]{"player"}));
        assertTrue(manager.dispatch(sender, "ah", new String[]{"ci"}));
        assertTrue(manager.dispatch(sender, "ah", new String[]{"a", "delete", "30"}));

        assertTrue(executions.isEmpty());
        assertEquals(List.of(defaultCommand, defaultCommand, directCommand, nestedCommand), guarded);
        assertArrayEquals(new String[0], contexts.get(0).args());
        assertArrayEquals(new String[]{"player"}, contexts.get(1).args());
        assertArrayEquals(new String[]{"ci"}, contexts.get(2).args());
        assertArrayEquals(new String[]{"delete", "30"}, contexts.get(3).args());
        assertTrue(contexts.stream().allMatch(context -> context.sender() == sender && context.label().equals("ah")));

        manager.setExecutionGuard((command, context) -> true);
        manager.dispatch(sender, "ah", new String[0]);
        manager.setExecutionGuard(null);
        manager.dispatch(sender, "ah", new String[]{"a", "delete", "30"});

        assertEquals(2, executions.size());
        assertArrayEquals(new String[0], executions.get(0).args());
        assertArrayEquals(new String[]{"delete", "30"}, executions.get(1).args());
    }

    @Test
    void validatesCommandsBeforeCallingTheExecutionGuard() {
        int[] guarded = {0};
        Command defaultCommand = playerCommand(List.of("open"), "auction.open", "[player]", 0, 1);
        CommandManager manager = new CommandManager(
                (rootLabel, dispatcher) -> { }, MESSAGES, "auction", "auction.root", defaultCommand);
        manager.addSection("admin").addCommand(playerCommand(
                List.of("purge"), "auction.purge", "<days>", 2, 2));
        manager.setExecutionGuard((command, context) -> {
            guarded[0]++;
            return false;
        });
        TestSender permitted = new TestSender("auction.root", "auction.open", "auction.purge");
        TestSender console = new TestSender(true, "auction.root", "auction.open", "auction.purge");

        manager.dispatch(new TestSender(), "auction", new String[0]);
        manager.dispatch(new TestSender("auction.root"), "auction", new String[0]);
        manager.dispatch(new TestSender("auction.root"), "auction", new String[]{"player"});
        manager.dispatch(console, "auction", new String[0]);
        manager.dispatch(console, "auction", new String[]{"player"});
        manager.dispatch(permitted, "auction", new String[]{"player", "extra"});
        manager.dispatch(new TestSender(), "auction", new String[]{"admin", "purge", "30"});
        manager.dispatch(console, "auction", new String[]{"admin", "purge", "30"});
        manager.dispatch(permitted, "auction", new String[]{"admin", "purge"});

        assertEquals(0, guarded[0]);

        manager.dispatch(permitted, "auction", new String[0]);
        manager.dispatch(permitted, "auction", new String[]{"admin", "purge", "30"});

        assertEquals(2, guarded[0]);

        manager.setDefaultCommand(new TestCommand(List.of("open"), "auction.open", "[player]", 0, 1, false,
                ctx -> { throw new AssertionError("Rejected validation must not execute the command"); },
                ctx -> List.of(), false));
        manager.dispatch(permitted, "auction", new String[0]);
        manager.dispatch(permitted, "auction", new String[]{"player"});

        assertEquals(2, guarded[0]);
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
        return new TestCommand(aliases, permission, usage, minArgs, maxArgs, true, execute, tabComplete, true);
    }

    private static TestCommand playerCommand(
            List<String> aliases,
            String permission,
            String usage,
            int minArgs,
            int maxArgs
    ) {
        return new TestCommand(aliases, permission, usage, minArgs, maxArgs, false, ctx -> { }, ctx -> List.of(), true);
    }

    private record TestCommand(
            List<String> aliases,
            String permission,
            String usage,
            int minArgs,
            int maxArgs,
            boolean console,
            Consumer<CommandContext> execution,
            Function<CommandContext, List<String>> completion,
            boolean valid
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
        public boolean validate(CommandContext ctx) {
            return valid;
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
        private final List<Component> components = new ArrayList<>();
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
            components.add(component);
        }

        private List<String> messages() {
            return components.stream().map(PLAIN::serialize).toList();
        }
    }
}
