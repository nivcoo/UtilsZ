package fr.nivcoo.utilsz.platform.bukkit.item;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.view.MerchantView;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginItemRegistryTest {

    @Test
    void handlesManagedBreaksAfterNormalProtectionListeners() throws NoSuchMethodException {
        EventHandler handler = PluginItemRegistry.class
                .getDeclaredMethod("onBreak", BlockBreakEvent.class)
                .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void claimsManagedBlockBreakBeforeCallingPlugin() {
        Block target = solidBlock();
        TestPluginBlock pluginBlock = new TestPluginBlock(target, true, true);
        PluginItemRegistry registry = new PluginItemRegistry(null).register(pluginBlock);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(target);

        registry.onBreak(event);

        verify(event).setDropItems(false);
        verify(event).setCancelled(true);
        assertEquals(1, pluginBlock.breakCallbacks);
    }

    @Test
    void claimsManagedExplosionBlocksBeforeTrackersAndQueuesAtMonitor() throws NoSuchMethodException {
        assertExplosionHandler("onEntityExplodePrepare", EntityExplodeEvent.class,
                EventPriority.HIGHEST, true);
        assertExplosionHandler("onEntityExplodeCommit", EntityExplodeEvent.class,
                EventPriority.MONITOR, false);
        assertExplosionHandler("onBlockExplodePrepare", BlockExplodeEvent.class,
                EventPriority.HIGHEST, true);
        assertExplosionHandler("onBlockExplodeCommit", BlockExplodeEvent.class,
                EventPriority.MONITOR, false);
    }

    @Test
    void commitsClaimedExplosionBlockExactlyOnce() {
        Block target = solidBlock();
        TestPluginBlock pluginBlock = new TestPluginBlock(target, true, true);
        DeferredTasks deferred = new DeferredTasks();
        PluginItemRegistry registry = new PluginItemRegistry(null, deferred::add).register(pluginBlock);
        EntityExplodeEvent event = explosion(target, false);

        registry.onEntityExplodePrepare(event);

        assertTrue(event.blockList().isEmpty());
        assertEquals(0, pluginBlock.destroyAttempts);

        registry.onEntityExplodeCommit(event);
        registry.onEntityExplodeCommit(event);

        assertEquals(0, pluginBlock.destroyAttempts);
        deferred.runAll();
        assertEquals(1, pluginBlock.destroyAttempts);
        verify(target, times(1)).setType(Material.AIR, false);
    }

    @Test
    void leavesUnmanagedBlocksInVanillaExplosion() {
        Block managed = solidBlock();
        Block vanilla = solidBlock();
        TestPluginBlock pluginBlock = new TestPluginBlock(managed, true, true);
        DeferredTasks deferred = new DeferredTasks();
        PluginItemRegistry registry = new PluginItemRegistry(null, deferred::add).register(pluginBlock);
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(new ArrayList<>(List.of(managed, vanilla)));
        when(event.isCancelled()).thenReturn(false);

        registry.onEntityExplodePrepare(event);

        assertEquals(List.of(vanilla), event.blockList());
        verify(vanilla, never()).setType(Material.AIR, false);

        registry.onEntityExplodeCommit(event);
        deferred.runAll();

        assertEquals(1, pluginBlock.destroyAttempts);
        verify(managed, times(1)).setType(Material.AIR, false);
        verify(vanilla, never()).setType(Material.AIR, false);
    }

    @Test
    void abandonsClaimedExplosionBlockWhenEventIsCancelled() {
        Block target = solidBlock();
        TestPluginBlock pluginBlock = new TestPluginBlock(target, true, true);
        DeferredTasks deferred = new DeferredTasks();
        PluginItemRegistry registry = new PluginItemRegistry(null, deferred::add).register(pluginBlock);
        EntityExplodeEvent event = explosion(target, true);

        registry.onEntityExplodePrepare(event);
        registry.onEntityExplodeCommit(event);
        deferred.runAll();

        assertEquals(0, pluginBlock.destroyAttempts);
        verify(target, never()).setType(Material.AIR, false);
    }

    @Test
    void abandonsClaimedExplosionBlockWhenCancelledAfterMonitor() {
        Block target = solidBlock();
        TestPluginBlock pluginBlock = new TestPluginBlock(target, true, true);
        DeferredTasks deferred = new DeferredTasks();
        PluginItemRegistry registry = new PluginItemRegistry(null, deferred::add).register(pluginBlock);
        EntityExplodeEvent event = explosion(target, false);

        registry.onEntityExplodePrepare(event);
        registry.onEntityExplodeCommit(event);
        when(event.isCancelled()).thenReturn(true);
        deferred.runAll();

        assertEquals(0, pluginBlock.destroyAttempts);
        verify(target, never()).setType(Material.AIR, false);
    }

    @Test
    void keepsClaimedExplosionBlockWhenPluginRollbackFails() {
        Block target = solidBlock();
        TestPluginBlock pluginBlock = new TestPluginBlock(target, true, false);
        DeferredTasks deferred = new DeferredTasks();
        PluginItemRegistry registry = new PluginItemRegistry(null, deferred::add).register(pluginBlock);
        EntityExplodeEvent event = explosion(target, false);

        registry.onEntityExplodePrepare(event);
        registry.onEntityExplodeCommit(event);
        deferred.runAll();

        assertEquals(1, pluginBlock.destroyAttempts);
        verify(target, never()).setType(Material.AIR, false);
    }

    @Test
    void keepsProtectedExplosionBlockOutOfVanillaExplosion() {
        Block target = solidBlock();
        TestPluginBlock pluginBlock = new TestPluginBlock(target, false, true);
        DeferredTasks deferred = new DeferredTasks();
        PluginItemRegistry registry = new PluginItemRegistry(null, deferred::add).register(pluginBlock);
        EntityExplodeEvent event = explosion(target, false);

        registry.onEntityExplodePrepare(event);
        registry.onEntityExplodeCommit(event);
        deferred.runAll();

        assertTrue(event.blockList().isEmpty());
        assertEquals(0, pluginBlock.destroyAttempts);
        verify(target, never()).setType(Material.AIR, false);
    }

    @Test
    void rejectsPluginItemForGenericVanillaIngredient() {
        ItemStack questBook = stack();
        TestPluginItem item = new TestPluginItem(questBook);

        assertTrue(PluginItemRegistry.containsUnexpectedPluginItem(
                Set.of(item), new ItemStack[]{questBook, null}, List.of(stack())));
    }

    @Test
    void acceptsPluginItemWhenRecipeExplicitlyRequestsExactItem() {
        ItemStack questBook = stack();
        ItemStack explicitIngredient = stack();
        when(explicitIngredient.isSimilar(questBook)).thenReturn(true);
        TestPluginItem item = new TestPluginItem(questBook, explicitIngredient);

        assertFalse(PluginItemRegistry.containsUnexpectedPluginItem(
                Set.of(item), new ItemStack[]{questBook, null}, List.of(explicitIngredient)));
    }

    @Test
    void requiresOneExplicitIngredientPerPluginItemInput() {
        ItemStack firstQuestBook = stack();
        ItemStack secondQuestBook = stack();
        ItemStack explicitIngredient = stack();
        when(explicitIngredient.isSimilar(firstQuestBook)).thenReturn(true);
        when(explicitIngredient.isSimilar(secondQuestBook)).thenReturn(true);
        TestPluginItem item = new TestPluginItem(firstQuestBook, secondQuestBook, explicitIngredient);

        assertTrue(PluginItemRegistry.containsUnexpectedPluginItem(
                Set.of(item),
                new ItemStack[]{firstQuestBook, secondQuestBook},
                List.of(explicitIngredient, stack())));
    }

    @Test
    void rejectsDifferentItemEvenWhenPluginItemIdMatches() {
        ItemStack questBook = stack();
        ItemStack differentExplicitIngredient = stack();
        TestPluginItem item = new TestPluginItem(questBook, differentExplicitIngredient);

        assertTrue(PluginItemRegistry.containsUnexpectedPluginItem(
                Set.of(item), new ItemStack[]{questBook, null}, List.of(differentExplicitIngredient)));
    }

    @Test
    void exactRecipeDoesNotAuthorizeAnotherGenericSelectedTrade() {
        ItemStack questBook = stack();
        ItemStack explicitIngredient = stack();
        ItemStack genericBookIngredient = stack();
        when(explicitIngredient.isSimilar(questBook)).thenReturn(true);
        TestPluginItem item = new TestPluginItem(questBook, explicitIngredient);

        MerchantRecipe exactRecipe = recipe(explicitIngredient);
        MerchantRecipe genericRecipe = recipe(genericBookIngredient);
        Merchant merchant = mock(Merchant.class);
        when(merchant.getRecipes()).thenReturn(List.of(exactRecipe, genericRecipe));
        MerchantInventory inventory = mock(MerchantInventory.class);
        when(inventory.getMerchant()).thenReturn(merchant);

        PluginItemRegistry registry = new PluginItemRegistry(null).register(item);
        when(inventory.getSelectedRecipeIndex()).thenReturn(1);
        assertTrue(registry.rejectsMerchantItem(questBook, inventory));

        when(inventory.getSelectedRecipeIndex()).thenReturn(0);
        assertFalse(registry.rejectsMerchantItem(questBook, inventory));
    }

    @Test
    void replacesAutofilledQuestBookWithOrdinaryBooksBeforePurchase() {
        Material book = mock(Material.class);
        ItemStack questBook = merchantStack(book, 1);
        ItemStack ordinaryBooks = merchantStack(book, 3);
        ItemStack otherBooks = merchantStack(book, 2);
        MerchantSession session = new MerchantSession(questBook, ordinaryBooks, otherBooks);
        PluginItemRegistry registry = new PluginItemRegistry(null, session.deferred::add)
                .register(new TestPluginItem(questBook));

        try (var delivery = mockStatic(ItemDelivery.class)) {
            registry.onTradeSelect(session.selection);
            assertEquals(questBook, session.inputs[1]);
            session.deferred.runAll();

            assertEquals(5, session.inputs[1].getAmount());
            assertEquals(questBook.getType(), session.inputs[1].getType());
            assertEquals(1, questBook.getAmount());
            assertNull(session.storage[0]);
            assertNull(session.storage[1]);
            assertEquals(session.emeralds, session.inputs[0]);
            delivery.verify(() -> ItemDelivery.giveOrDrop(session.player, new ItemStack[]{questBook}), times(1));

            registry.onMerchantClick(session.resultClick);
            registry.onMerchantPurchase(session.purchase);
            verify(session.resultClick, never()).setCancelled(true);
            verify(session.purchase, never()).setCancelled(true);
            session.deferred.runAll();
            delivery.verifyNoMoreInteractions();
        }
    }

    @Test
    void rejectsPurchaseUsingQuestBookAndDoesNotRefillWithAnotherPluginItem() {
        Material book = mock(Material.class);
        ItemStack questBook = merchantStack(book, 1);
        ItemStack anotherQuestBook = merchantStack(book, 1);
        MerchantSession session = new MerchantSession(questBook, anotherQuestBook);
        PluginItemRegistry registry = new PluginItemRegistry(null, session.deferred::add)
                .register(new TestPluginItem(questBook, anotherQuestBook));

        try (var delivery = mockStatic(ItemDelivery.class)) {
            registry.onMerchantClick(session.resultClick);
            registry.onMerchantPurchase(session.purchase);
            verify(session.resultClick).setCancelled(true);
            verify(session.purchase).setCancelled(true);
            session.deferred.runAll();

            assertNull(session.inputs[1]);
            assertEquals(anotherQuestBook, session.storage[0]);
            assertEquals(session.emeralds, session.inputs[0]);
            delivery.verify(() -> ItemDelivery.giveOrDrop(session.player, new ItemStack[]{questBook}), times(1));
        }
    }

    @Test
    void allowsOrdinaryTradeWithQuestBookOnlyInPlayerInventory() {
        Material book = mock(Material.class);
        ItemStack questBook = merchantStack(book, 1);
        ItemStack ordinaryBook = merchantStack(book, 1);
        MerchantSession session = new MerchantSession(ordinaryBook, questBook);
        PluginItemRegistry registry = new PluginItemRegistry(null, session.deferred::add)
                .register(new TestPluginItem(questBook));

        try (var delivery = mockStatic(ItemDelivery.class)) {
            registry.onTradeSelect(session.selection);
            session.deferred.runAll();
            registry.onMerchantClick(session.resultClick);
            registry.onMerchantPurchase(session.purchase);

            verify(session.resultClick, never()).setCancelled(true);
            verify(session.purchase, never()).setCancelled(true);
            assertEquals(ordinaryBook, session.inputs[1]);
            assertEquals(questBook, session.storage[0]);
            delivery.verifyNoInteractions();
        }
    }

    @Test
    void skipsDeferredMerchantChangesAfterInventoryCloses() {
        ItemStack questBook = merchantStack(mock(Material.class), 1);
        MerchantSession session = new MerchantSession(questBook);
        PluginItemRegistry registry = new PluginItemRegistry(null, session.deferred::add)
                .register(new TestPluginItem(questBook));

        registry.onTradeSelect(session.selection);
        when(session.player.getOpenInventory()).thenReturn(mock(MerchantView.class));
        session.deferred.runAll();

        assertEquals(questBook, session.inputs[1]);
    }

    @Test
    void capsRefillAndSkipsItemsWithDifferentMetadata() {
        Material book = mock(Material.class);
        ItemStack questBook = merchantStack(book, 1);
        ItemStack foreignBook = merchantStack(book, 8);
        MerchantSession session = new MerchantSession(questBook, foreignBook,
                merchantStack(book, 40), merchantStack(book, 40));
        when(session.purchase.getTrade().getIngredients().get(1).isSimilar(foreignBook)).thenReturn(false);
        PluginItemRegistry registry = new PluginItemRegistry(null, session.deferred::add)
                .register(new TestPluginItem(questBook));

        try (var delivery = mockStatic(ItemDelivery.class)) {
            registry.onTradeSelect(session.selection);
            session.deferred.runAll();

            assertEquals(64, session.inputs[1].getAmount());
            assertEquals(foreignBook, session.storage[0]);
            assertEquals(8, foreignBook.getAmount());
            assertNull(session.storage[1]);
            assertEquals(16, session.storage[2].getAmount());
            delivery.verify(() -> ItemDelivery.giveOrDrop(session.player, new ItemStack[]{questBook}), times(1));
        }
    }

    @Test
    void skipsDeferredMerchantChangesAfterAnotherTradeIsSelected() {
        ItemStack questBook = merchantStack(mock(Material.class), 1);
        MerchantSession session = new MerchantSession(questBook);
        PluginItemRegistry registry = new PluginItemRegistry(null, session.deferred::add)
                .register(new TestPluginItem(questBook));

        registry.onTradeSelect(session.selection);
        when(session.selection.getView().getTopInventory().getSelectedRecipeIndex()).thenReturn(1);
        session.deferred.runAll();

        assertEquals(questBook, session.inputs[1]);
    }

    private static ItemStack merchantStack(Material material, int amount) {
        ItemStack stack = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenAnswer(invocation -> count.get());
        when(stack.getMaxStackSize()).thenReturn(64);
        doAnswer(invocation -> {
            count.set(invocation.getArgument(0));
            return null;
        }).when(stack).setAmount(anyInt());
        when(stack.clone()).thenAnswer(invocation -> merchantStack(material, count.get()));
        when(stack.isSimilar(any())).thenAnswer(invocation -> {
            ItemStack other = invocation.getArgument(0);
            return other != null && other.getType() == material;
        });
        return stack;
    }

    private static final class MerchantSession {
        private final DeferredTasks deferred = new DeferredTasks();
        private final Player player = mock(Player.class);
        private final ItemStack emeralds = merchantStack(mock(Material.class), 10);
        private final ItemStack[] inputs;
        private final ItemStack[] storage;
        private final TradeSelectEvent selection = mock(TradeSelectEvent.class);
        private final InventoryClickEvent resultClick = mock(InventoryClickEvent.class);
        private final PlayerPurchaseEvent purchase = mock(PlayerPurchaseEvent.class);

        private MerchantSession(ItemStack book, ItemStack... storage) {
            this.inputs = new ItemStack[]{emeralds, book};
            this.storage = storage;
            PlayerInventory playerInventory = mock(PlayerInventory.class);
            when(playerInventory.getStorageContents()).thenReturn(storage);
            when(playerInventory.getItem(anyInt())).thenAnswer(invocation -> storage[invocation.<Integer>getArgument(0)]);
            doAnswer(invocation -> {
                storage[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
                return null;
            }).when(playerInventory).setItem(anyInt(), any());
            when(player.getInventory()).thenReturn(playerInventory);
            MerchantRecipe recipe = mock(MerchantRecipe.class);
            List<ItemStack> ingredients = List.of(
                    merchantStack(emeralds.getType(), 10), merchantStack(book.getType(), 1));
            when(recipe.getIngredients()).thenReturn(ingredients);
            Merchant merchant = mock(Merchant.class);
            when(merchant.getRecipes()).thenReturn(List.of(recipe));
            MerchantInventory inventory = mock(MerchantInventory.class);
            when(inventory.getMerchant()).thenReturn(merchant);
            when(inventory.getSelectedRecipe()).thenReturn(recipe);
            when(inventory.getMaxStackSize()).thenReturn(64);
            when(inventory.getItem(anyInt())).thenAnswer(invocation -> inputs[invocation.<Integer>getArgument(0)]);
            doAnswer(invocation -> {
                inputs[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
                return null;
            }).when(inventory).setItem(anyInt(), any());
            MerchantView view = mock(MerchantView.class);
            when(view.getTopInventory()).thenReturn(inventory);
            when(view.getBottomInventory()).thenReturn(playerInventory);
            when(player.getOpenInventory()).thenReturn(view);
            when(selection.getWhoClicked()).thenReturn(player);
            when(selection.getView()).thenReturn(view);
            when(resultClick.getWhoClicked()).thenReturn(player);
            when(resultClick.getView()).thenReturn(view);
            when(resultClick.getRawSlot()).thenReturn(2);
            when(resultClick.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
            when(purchase.getPlayer()).thenReturn(player);
            when(purchase.getTrade()).thenReturn(recipe);
        }
    }

    private static MerchantRecipe recipe(ItemStack ingredient) {
        MerchantRecipe recipe = mock(MerchantRecipe.class);
        when(recipe.getIngredients()).thenReturn(List.of(ingredient));
        return recipe;
    }

    private static ItemStack stack() {
        ItemStack stack = mock(ItemStack.class);
        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        when(stack.getType()).thenReturn(material);
        return stack;
    }

    private static void assertExplosionHandler(String methodName, Class<?> eventType,
                                               EventPriority priority, boolean ignoreCancelled)
            throws NoSuchMethodException {
        EventHandler handler = PluginItemRegistry.class.getDeclaredMethod(methodName, eventType)
                .getAnnotation(EventHandler.class);
        assertEquals(priority, handler.priority());
        assertEquals(ignoreCancelled, handler.ignoreCancelled());
    }

    private static Block solidBlock() {
        Block block = mock(Block.class);
        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        when(block.getType()).thenReturn(material);
        return block;
    }

    private static EntityExplodeEvent explosion(Block target, boolean cancelled) {
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(new ArrayList<>(List.of(target)));
        when(event.isCancelled()).thenReturn(cancelled);
        return event;
    }

    private static final class DeferredTasks {
        private final List<Runnable> tasks = new ArrayList<>();

        private void add(Runnable task) {
            tasks.add(task);
        }

        private void runAll() {
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    private static final class TestPluginItem extends PluginItem<Object> {
        private final Set<ItemStack> matchingStacks = Collections.newSetFromMap(new IdentityHashMap<>());

        private TestPluginItem(ItemStack... matchingStacks) {
            super(null);
            Collections.addAll(this.matchingStacks, matchingStacks);
        }

        @Override
        public String id() {
            return "test-item";
        }

        @Override
        protected ItemStack buildItem(Object data) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void writeData(ItemStack item, Object data) {
        }

        @Override
        protected Optional<Object> readData(ItemStack item) {
            return Optional.of(new Object());
        }

        @Override
        public boolean matches(ItemStack item) {
            return matchingStacks.contains(item);
        }
    }

    private static final class TestPluginBlock extends PluginBlock<Object> {
        private final Block target;
        private final boolean destroyAllowed;
        private final boolean destroySuccessful;
        private int destroyAttempts;
        private int breakCallbacks;

        private TestPluginBlock(Block target, boolean destroyAllowed, boolean destroySuccessful) {
            super(null);
            this.target = target;
            this.destroyAllowed = destroyAllowed;
            this.destroySuccessful = destroySuccessful;
        }

        @Override
        public String id() {
            return "test-block";
        }

        @Override
        public Optional<Object> read(Block block) {
            return block == target ? Optional.of(this) : Optional.empty();
        }

        @Override
        public boolean shouldDestroy(Object data, PluginBlockDestroyContext context) {
            return destroyAllowed;
        }

        @Override
        public boolean tryDestroy(Object data, PluginBlockDestroyContext context) {
            destroyAttempts++;
            if (destroySuccessful) context.block().setType(Material.AIR, false);
            return destroySuccessful;
        }

        @Override
        public void onBreak(org.bukkit.entity.Player player, Object data, BlockBreakEvent event) {
            breakCallbacks++;
        }
    }
}
