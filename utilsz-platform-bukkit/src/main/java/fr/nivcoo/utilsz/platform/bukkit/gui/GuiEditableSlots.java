package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public final class GuiEditableSlots {
    private static final Validator ACCEPT_ALL = (inventory, item) -> Validation.allow();
    private static final GuiEditableSlots NONE = new GuiEditableSlots(List.of(), false, ACCEPT_ALL);

    private final List<Integer> slots;
    private final boolean dragAllowed;
    private final Validator validator;

    private GuiEditableSlots(List<Integer> slots, boolean dragAllowed, Validator validator) {
        this.slots = slots;
        this.dragAllowed = dragAllowed;
        this.validator = validator;
    }

    public static GuiEditableSlots none() {
        return NONE;
    }

    public static GuiEditableSlots of(Collection<Integer> slots) {
        if (slots == null || slots.isEmpty()) return NONE;
        return new GuiEditableSlots(List.copyOf(slots), false, ACCEPT_ALL);
    }

    public GuiEditableSlots allowDrag() {
        if (dragAllowed) return this;
        return new GuiEditableSlots(slots, true, validator);
    }

    public GuiEditableSlots validateWith(Validator validator) {
        return new GuiEditableSlots(slots, dragAllowed,
                Objects.requireNonNull(validator, "validator"));
    }

    public List<Integer> slots() {
        return slots;
    }

    public boolean dragAllowed() {
        return dragAllowed;
    }

    Validation validate(GuiInventory inventory, ItemStack item) {
        Validation validation = validator.validate(inventory, item);
        return Objects.requireNonNull(validation, "validator result");
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
}
