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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
    void destroysManagedExplosionBlocksOnlyAfterProtectionListeners() throws NoSuchMethodException {
        assertEquals(EventPriority.MONITOR, PluginItemRegistry.class
                .getDeclaredMethod("onEntityExplode", EntityExplodeEvent.class)
                .getAnnotation(EventHandler.class).priority());
        assertEquals(EventPriority.MONITOR, PluginItemRegistry.class
                .getDeclaredMethod("onBlockExplode", BlockExplodeEvent.class)
                .getAnnotation(EventHandler.class).priority());
    }

    @Test
    void defaultTryDestroyKeepsLegacyOnDestroyImplementationsCompatible() {
        AtomicBoolean destroyed = new AtomicBoolean();
        PluginBlock<Object> block = new PluginBlock<>(null) {
            @Override
            public String id() {
                return "legacy-block";
            }

            @Override
            public Optional<Object> read(Block ignored) {
                return Optional.empty();
            }

            @Override
            public void onDestroy(Object ignored, PluginBlockDestroyContext context) {
                destroyed.set(true);
            }
        };

        assertTrue(block.tryDestroy(new Object(), null));
        assertTrue(destroyed.get());
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
}
