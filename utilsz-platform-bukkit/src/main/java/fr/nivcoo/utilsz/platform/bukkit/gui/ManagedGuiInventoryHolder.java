package fr.nivcoo.utilsz.platform.bukkit.gui;

import org.bukkit.inventory.InventoryHolder;

public interface ManagedGuiInventoryHolder extends InventoryHolder {

    default boolean isManagedGuiInventory() {
        return true;
    }
}
