package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unused")
public final class GuiEditableSlots {
    private static final Validator ACCEPT_ALL = (inventory, item) -> Validation.allow();
    private static final GuiEditableSlots NONE = new GuiEditableSlots(List.of());

    private final List<Region> regions;
    private final List<Integer> slots;
    private final Map<Integer, Region> bySlot;

    private GuiEditableSlots(List<Region> regions) {
        this.regions = List.copyOf(regions);
        Map<Integer, Region> indexed = new LinkedHashMap<>();
        for (Region region : regions) {
            for (int slot : region.slots()) {
                if (slot < 0) throw new IllegalArgumentException("editable slot cannot be negative: " + slot);
                if (indexed.putIfAbsent(slot, region) != null) {
                    throw new IllegalArgumentException("editable slot belongs to multiple regions: " + slot);
                }
            }
        }
        this.bySlot = Map.copyOf(indexed);
        this.slots = List.copyOf(indexed.keySet());
    }

    public static GuiEditableSlots none() {
        return NONE;
    }

    public static GuiEditableSlots of(Collection<Integer> slots) {
        if (slots == null || slots.isEmpty()) return NONE;
        return builder().region(slots, false, true, ACCEPT_ALL).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public GuiEditableSlots allowDrag() {
        if (regions.stream().allMatch(Region::dragAllowed)) return this;
        return new GuiEditableSlots(regions.stream()
                .map(region -> new Region(region.slots(), true, region.shiftClickAllowed(), region.validator()))
                .toList());
    }

    public GuiEditableSlots validateWith(Validator validator) {
        Validator checked = Objects.requireNonNull(validator, "validator");
        return new GuiEditableSlots(regions.stream()
                .map(region -> new Region(region.slots(), region.dragAllowed(), region.shiftClickAllowed(), checked))
                .toList());
    }

    public List<Integer> slots() {
        return slots;
    }

    public boolean dragAllowed() {
        return !regions.isEmpty() && regions.stream().allMatch(Region::dragAllowed);
    }

    public boolean dragAllowed(int slot) {
        Region region = bySlot.get(slot);
        return region != null && region.dragAllowed();
    }

    public boolean shiftClickAllowed(int slot) {
        Region region = bySlot.get(slot);
        return region != null && region.shiftClickAllowed();
    }

    Validation validate(GuiInventory inventory, ItemStack item) {
        if (regions.isEmpty()) return Validation.reject();
        return Objects.requireNonNull(regions.getFirst().validator().validate(inventory, item), "validator result");
    }

    Validation validate(GuiInventory inventory, int slot, ItemStack item) {
        Region region = bySlot.get(slot);
        if (region == null) return Validation.reject();
        return Objects.requireNonNull(region.validator().validate(inventory, item), "validator result");
    }

    @FunctionalInterface
    public interface Validator {
        Validation validate(GuiInventory inventory, ItemStack item);
    }

    public record Validation(boolean accepted, Component rejectionMessage) {
        private static final Validation ACCEPTED = new Validation(true, null);
        private static final Validation REJECTED = new Validation(false, null);

        public static Validation allow() {
            return ACCEPTED;
        }

        public static Validation reject() {
            return REJECTED;
        }

        public static Validation reject(Component message) {
            return message == null ? REJECTED : new Validation(false, message);
        }
    }

    public static final class Builder {
        private final List<Region> regions = new ArrayList<>();
        private final Set<Integer> occupied = new HashSet<>();

        public Builder region(Collection<Integer> slots, boolean allowDrag, boolean allowShiftClick,
                              Validator validator) {
            if (slots == null || slots.isEmpty()) return this;
            List<Integer> copy = List.copyOf(slots);
            for (Integer slot : copy) {
                if (slot == null || slot < 0) throw new IllegalArgumentException("invalid editable slot: " + slot);
                if (!occupied.add(slot)) throw new IllegalArgumentException("editable slot belongs to multiple regions: " + slot);
            }
            regions.add(new Region(copy, allowDrag, allowShiftClick,
                    Objects.requireNonNull(validator, "validator")));
            return this;
        }

        public GuiEditableSlots build() {
            return regions.isEmpty() ? NONE : new GuiEditableSlots(regions);
        }
    }

    private record Region(List<Integer> slots, boolean dragAllowed, boolean shiftClickAllowed,
                          Validator validator) {
    }
}
