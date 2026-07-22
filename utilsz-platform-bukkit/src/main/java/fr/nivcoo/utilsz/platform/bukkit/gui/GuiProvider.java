package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryCloseEvent;

public interface GuiProvider {

    Component title(GuiInventory inv);
    int rows(GuiInventory inv);

    void init(GuiInventory inv);
    void update(GuiInventory inv);

    default void refresh(GuiInventory inv) { update(inv); }

    default int updatePeriodTicks() { return 1; }
    default boolean needsUpdate(GuiInventory inv) { return true; }

    default boolean cancelBottomClicks() { return false; }
    default boolean cancelBottomClicks(GuiInventory inv) { return cancelBottomClicks(); }
    default GuiEditableSlots editableSlots(GuiInventory inv) { return GuiEditableSlots.none(); }

    default boolean allowClose(GuiInventory inv) { return true; }
    default void onEditableChange(GuiInventory inv) { }
    default void onQuit(GuiInventory inv) { }
    default void onClose(InventoryCloseEvent e, GuiInventory inv) {}
}
