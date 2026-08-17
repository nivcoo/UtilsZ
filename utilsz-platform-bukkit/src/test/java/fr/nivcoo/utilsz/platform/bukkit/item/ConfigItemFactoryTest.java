package fr.nivcoo.utilsz.platform.bukkit.item;

import fr.nivcoo.utilsz.platform.bukkit.gui.ConfigGuiItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigItemFactoryTest {

    @Test
    void resolvesAndCopiesGenericAmounts() {
        ConfigItem item = new ConfigItem();
        item.amount = 64;
        assertEquals(64, ConfigItemFactory.amount(item));
        assertEquals(64, ConfigItemFactory.copy(item).amount);

        ConfigGuiItem guiItem = new ConfigGuiItem();
        guiItem.amount = 5;
        assertEquals(5, ConfigItemFactory.amount(guiItem));
        assertEquals(5, ConfigItemFactory.copy(guiItem).amount);

        item.amount = 0;
        assertEquals(1, ConfigItemFactory.amount(item));
    }
}
