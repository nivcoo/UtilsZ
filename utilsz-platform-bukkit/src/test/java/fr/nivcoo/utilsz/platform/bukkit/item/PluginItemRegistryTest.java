package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Material;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
