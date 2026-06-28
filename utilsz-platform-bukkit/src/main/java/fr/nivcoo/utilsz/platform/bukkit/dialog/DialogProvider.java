package fr.nivcoo.utilsz.platform.bukkit.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DialogProvider {

    Component title(DialogView view);

    default void init(DialogView view) {
    }

    default @Nullable Component externalTitle(DialogView view) {
        return null;
    }

    default boolean canCloseWithEscape(DialogView view) {
        return true;
    }

    default boolean pause(DialogView view) {
        return false;
    }

    default DialogBase.DialogAfterAction afterAction(DialogView view) {
        return DialogBase.DialogAfterAction.CLOSE;
    }

    default List<DialogBody> body(DialogView view) {
        return List.of();
    }

    default List<DialogInput> inputs(DialogView view) {
        return List.of();
    }

    default List<ActionButton> buttons(DialogView view) {
        return List.of();
    }

    default @Nullable ActionButton exitButton(DialogView view) {
        return null;
    }

    default int columns(DialogView view) {
        return 1;
    }

    default DialogType type(DialogView view, List<ActionButton> buttons, @Nullable ActionButton exitButton) {
        if (buttons.isEmpty()) {
            return exitButton == null ? DialogType.notice() : DialogType.notice(exitButton);
        }
        if (buttons.size() == 2 && exitButton == null) {
            return DialogType.confirmation(buttons.get(0), buttons.get(1));
        }
        return DialogType.multiAction(buttons, exitButton, Math.max(1, columns(view)));
    }
}
