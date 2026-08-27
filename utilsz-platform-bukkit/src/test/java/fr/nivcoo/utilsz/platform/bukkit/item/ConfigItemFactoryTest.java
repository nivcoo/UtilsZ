package fr.nivcoo.utilsz.platform.bukkit.item;

import fr.nivcoo.utilsz.platform.bukkit.gui.ConfigGuiItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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

    @Test
    void deepCopiesArmorTrimConfiguration() {
        ConfigItem item = new ConfigItem();
        item.trim = new ConfigItem.ArmorTrimConfig();
        item.trim.material = "redstone";
        item.trim.pattern = "dune";

        ConfigItem copy = ConfigItemFactory.copy(item);

        assertNotSame(item.trim, copy.trim);
        assertEquals("redstone", copy.trim.material);
        assertEquals("dune", copy.trim.pattern);

        copy.trim.pattern = "vex";
        assertEquals("dune", item.trim.pattern);
    }
}
