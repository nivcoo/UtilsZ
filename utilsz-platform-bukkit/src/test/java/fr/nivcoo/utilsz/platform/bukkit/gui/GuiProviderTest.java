package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiProviderTest {

    @Test
    void refreshDelegatesToUpdateByDefault() {
        TrackingProvider provider = new TrackingProvider();

        provider.refresh(null);

        assertEquals(1, provider.updates);
    }

    @Test
    void configuredItemsTakePriorityOverEditableSlots() {
        assertTrue(GuiInventoryManager.editableTopSlot(List.of(10, 11), false, 10));
        assertFalse(GuiInventoryManager.editableTopSlot(List.of(10, 11), true, 10));
        assertFalse(GuiInventoryManager.editableTopSlot(List.of(10, 11), false, 12));
        assertFalse(GuiInventoryManager.editableTopSlot(null, false, 10));
    }

    private static final class TrackingProvider implements GuiProvider {

        private int updates;

        @Override
        public Component title(GuiInventory inventory) {
            return Component.empty();
        }

        @Override
        public int rows(GuiInventory inventory) {
            return 1;
        }

        @Override
        public void init(GuiInventory inventory) {
        }

        @Override
        public void update(GuiInventory inventory) {
            updates++;
        }
    }
}
