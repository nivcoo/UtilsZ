package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.ArrayList;
import java.util.List;

public interface GuiProvider {

    Component title(GuiInventory inv);
    int rows(GuiInventory inv);

    void init(GuiInventory inv);
    void update(GuiInventory inv);

    default int updatePeriodTicks() { return 1; }
    default boolean needsUpdate(GuiInventory inv) { return true; }

    default boolean cancelBottomClicks() { return false; }
    default boolean cancelTopDrag() { return true; }

    default boolean allowClose(GuiInventory inv) { return true; }
    default void onClose(InventoryCloseEvent e, GuiInventory inv) {}

    default List<Integer> excludeCases(GuiInventory inv) { return new ArrayList<>(); }
}
