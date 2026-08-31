package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginItemRegistryTest {

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
