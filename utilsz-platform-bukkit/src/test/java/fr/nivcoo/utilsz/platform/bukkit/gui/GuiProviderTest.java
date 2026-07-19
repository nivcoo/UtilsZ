package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiProviderTest {

    @Test
    void refreshDelegatesToUpdateByDefault() {
        TrackingProvider provider = new TrackingProvider();

        provider.refresh(null);

        assertEquals(1, provider.updates);
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
