package fr.nivcoo.utilsz.inventory;

import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.ArrayList;
import java.util.List;

public interface InventoryProvider {

    /* ---- Description / structure ---- */
    Component title(Inventory inv);
    int rows(Inventory inv);

    void init(Inventory inv);
    void update(Inventory inv);

    default int updatePeriodTicks() { return 1; }
    default boolean needsUpdate(Inventory inv) { return true; }

    default boolean cancelBottomClicks() { return false; }
    default boolean cancelTopDrag() { return true; }

    default boolean allowClose(Inventory inv) { return true; }
    default void onClose(InventoryCloseEvent e, Inventory inv) {}

    /* ---- Exceptions de clic autorisées ---- */
    default List<Integer> excludeCases(Inventory inv) { return new ArrayList<>(); }
}
