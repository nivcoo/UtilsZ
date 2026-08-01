package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiProviderTest {

    @Test
    void inventoryLayoutsKeepChestDefaultsAndSupportFixedContainers() {
        TrackingProvider provider = new TrackingProvider();

        assertEquals(GuiInventoryLayout.chest(1), provider.inventoryLayout(null));
        GuiInventoryLayout hopper = GuiInventoryLayout.fixed(
                GuiInventoryType.HOPPER, 5, 1);
        assertEquals(5, hopper.size());
        assertEquals(5, hopper.columns());
        assertEquals(1, hopper.rows());
        assertThrows(IllegalArgumentException.class,
                () -> GuiInventoryLayout.fixed(
                        GuiInventoryType.HOPPER, 9, 1));
        assertThrows(IllegalArgumentException.class,
                () -> GuiInventoryLayout.fixed(
                        GuiInventoryType.HOPPER, 1, 5));
    }

    @Test
    void editableSlotsCanInspectResolvedInventoryGeometry() {
        GuiInventoryLayout hopper = GuiInventoryLayout.fixed(
                GuiInventoryType.HOPPER, 5, 1);
        AtomicInteger inspections = new AtomicInteger();
        GuiProvider provider = new GuiProvider() {
            @Override
            public Component title(GuiInventory inventory) {
                throw new ConstructionStopped();
            }

            @Override
            public int rows(GuiInventory inventory) {
                return 1;
            }

            @Override
            public GuiInventoryLayout inventoryLayout(
                    GuiInventory inventory
            ) {
                return hopper;
            }

            @Override
            public GuiEditableSlots editableSlots(
                    GuiInventory inventory
            ) {
                assertEquals(hopper,
                        inventory.getInventoryLayout());
                assertEquals(5, inventory.getColumns());
                assertEquals(1, inventory.getRows());
                assertEquals(5, inventory.getSize());
                inspections.incrementAndGet();
                return GuiEditableSlots.of(List.of(4));
            }

            @Override
            public void init(GuiInventory inventory) {
            }

            @Override
            public void update(GuiInventory inventory) {
            }
        };

        assertThrows(ConstructionStopped.class,
                () -> new GuiInventory(
                        null, provider, null, null));

        assertEquals(1, inspections.get());
    }

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

    @Test
    void detectsDragsTouchingThePlayerInventory() {
        assertFalse(GuiInventoryManager.touchesBottom(Set.of(0, 8, 26), 27));
        assertTrue(GuiInventoryManager.touchesBottom(Set.of(0, 27), 27));
        assertTrue(GuiInventoryManager.touchesBottom(Set.of(44), 27));
        assertFalse(GuiInventoryManager.touchesBottom(null, 27));
    }

    @Test
    void blocksUnsafeInventoryActions() {
        assertTrue(GuiInventoryManager.blockedAction(InventoryAction.COLLECT_TO_CURSOR));
        assertTrue(GuiInventoryManager.blockedAction(InventoryAction.UNKNOWN));
        assertFalse(GuiInventoryManager.blockedAction(InventoryAction.CLONE_STACK));
        assertFalse(GuiInventoryManager.blockedAction(InventoryAction.PICKUP_ALL));
    }

    @Test
    void identifiesActionsThatDoNotMutateEditableSlots() {
        assertTrue(GuiInventoryManager.nonMutatingTopAction(InventoryAction.CLONE_STACK));
        assertTrue(GuiInventoryManager.nonMutatingTopAction(InventoryAction.NOTHING));
        assertFalse(GuiInventoryManager.nonMutatingTopAction(InventoryAction.DROP_ALL_SLOT));
        assertFalse(GuiInventoryManager.nonMutatingTopAction(InventoryAction.PICKUP_ALL));
    }

    @Test
    void shutdownRejectsPreparationAndOpeningBeforeMutation() {
        GuiInventoryManager manager =
                new GuiInventoryManager(null);

        manager.shutdown();

        assertThrows(
                IllegalStateException.class,
                () -> manager.prepare(null, null));
        assertThrows(
                IllegalStateException.class,
                () -> manager.open((GuiInventory) null));
        assertTrue(manager.getInventories().isEmpty());
    }

    @Test
    void editableSlotsCarryDragAndSinglePassValidationRules() {
        AtomicInteger validations = new AtomicInteger();
        GuiEditableSlots slots = GuiEditableSlots.of(List.of(10, 11))
                .allowDrag()
                .validateWith((inventory, item) -> {
                    validations.incrementAndGet();
                    return GuiEditableSlots.Validation.reject(Component.text("Refusé"));
                });

        assertEquals(List.of(10, 11), slots.slots());
        assertTrue(slots.dragAllowed());
        assertTrue(GuiEditableSlots.of(List.of(10)).validate(null, null).accepted());
        GuiEditableSlots.Validation rejected = slots.validate(null, null);
        assertFalse(rejected.accepted());
        assertEquals(Component.text("Refusé"), rejected.rejectionMessage());
        assertEquals(1, validations.get());
    }

    @Test
    void editableRegionsKeepIndependentPolicies() {
        GuiEditableSlots slots = GuiEditableSlots.builder()
                .region(List.of(10, 11), true, false,
                        (inventory, item) -> GuiEditableSlots.Validation.allow())
                .region(List.of(15), false, true,
                        (inventory, item) -> GuiEditableSlots.Validation.reject(Component.text("Refusé")))
                .build();

        assertTrue(slots.dragAllowed(10));
        assertFalse(slots.shiftClickAllowed(10));
        assertFalse(slots.dragAllowed(15));
        assertTrue(slots.shiftClickAllowed(15));
        assertTrue(slots.validate(null, 11, null).accepted());
        assertFalse(slots.validate(null, 15, null).accepted());
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

    private static final class ConstructionStopped
            extends RuntimeException {
    }

}
