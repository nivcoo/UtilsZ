package fr.nivcoo.utilsz.platform.bukkit.dialog;

import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItemFactory;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConfigDialogProvider implements DialogProvider {

    private final ConfigDialog config;
    private final Consumer<DialogView> actions;

    public ConfigDialogProvider(ConfigDialog config, Consumer<DialogView> actions) {
        this.config = Objects.requireNonNull(config, "config");
        this.actions = actions;
    }

    @Override
    public void init(DialogView view) {
        if (actions != null) actions.accept(view);
    }

    @Override
    public Component title(DialogView view) {
        return config.title == null ? Component.empty() : config.title;
    }

    @Override
    public @Nullable Component externalTitle(DialogView view) {
        return config.externalTitle;
    }

    @Override
    public boolean canCloseWithEscape(DialogView view) {
        return config.canCloseWithEscape;
    }

    @Override
    public boolean pause(DialogView view) {
        return config.pause;
    }

    @Override
    public DialogBase.DialogAfterAction afterAction(DialogView view) {
        return config.afterAction == null ? DialogBase.DialogAfterAction.CLOSE : config.afterAction;
    }

    @Override
    public List<DialogBody> body(DialogView view) {
        return config.body.stream().map(this::body).toList();
    }

    @Override
    public List<DialogInput> inputs(DialogView view) {
        return config.inputs.stream().map(this::input).toList();
    }

    @Override
    public List<ActionButton> buttons(DialogView view) {
        return config.buttons.stream().map(button -> button(view, button)).toList();
    }

    @Override
    public @Nullable ActionButton exitButton(DialogView view) {
        return config.exitButton == null ? null : button(view, config.exitButton);
    }

    @Override
    public int columns(DialogView view) {
        return config.columns;
    }

    @Override
    public DialogType type(DialogView view, List<ActionButton> buttons, @Nullable ActionButton exitButton) {
        ConfigDialogType type = config.type == null ? ConfigDialogType.MULTI_ACTION : config.type;
        return switch (type) {
            case NOTICE -> exitButton == null
                    ? DialogType.notice()
                    : DialogType.notice(exitButton);
            case CONFIRMATION -> {
                if (buttons.size() < 2) throw new IllegalArgumentException("confirmation dialogs need two buttons");
                yield DialogType.confirmation(buttons.get(0), buttons.get(1));
            }
            case MULTI_ACTION -> DialogType.multiAction(buttons, exitButton, Math.max(1, config.columns));
        };
    }

    private DialogBody body(ConfigDialogBody body) {
        ConfigDialogBodyType type = body.type == null ? ConfigDialogBodyType.TEXT : body.type;
        return switch (type) {
            case TEXT -> DialogBody.plainMessage(body.text == null ? Component.empty() : body.text, clamp(body.width, 1, 1024));
            case ITEM -> {
                PlainMessageDialogBody description = body.itemDescription == null
                        ? null
                        : DialogBody.plainMessage(body.itemDescription, clamp(body.itemDescriptionWidth, 1, 1024));
                yield DialogBody.item(ConfigItemFactory.create(body.item), description, body.showDecorations,
                        body.showTooltip, clamp(body.itemWidth, 1, 256), clamp(body.itemHeight, 1, 256));
            }
        };
    }

    private DialogInput input(ConfigDialogInput input) {
        ConfigDialogInputType type = input.type == null ? ConfigDialogInputType.TEXT : input.type;
        Component label = input.label == null ? Component.empty() : input.label;
        return switch (type) {
            case TEXT -> DialogInput.text(input.key, label)
                    .width(clamp(input.width, 1, 1024))
                    .labelVisible(input.labelVisible)
                    .initial(input.initialText == null ? "" : input.initialText)
                    .maxLength(Math.max(1, input.maxLength))
                    .multiline(input.maxLines == null && input.multilineHeight == null
                            ? null
                            : TextDialogInput.MultilineOptions.create(input.maxLines, input.multilineHeight))
                    .build();
            case BOOLEAN -> DialogInput.bool(input.key, label)
                    .initial(input.initialBoolean)
                    .onTrue(input.onTrue == null ? "true" : input.onTrue)
                    .onFalse(input.onFalse == null ? "false" : input.onFalse)
                    .build();
            case NUMBER_RANGE -> DialogInput.numberRange(input.key, label, input.start, input.end)
                    .width(clamp(input.width, 1, 1024))
                    .labelFormat(input.labelFormat == null ? "%s: %s" : input.labelFormat)
                    .initial(input.initialNumber)
                    .step(input.step)
                    .build();
            case SINGLE_OPTION -> DialogInput.singleOption(input.key, label, input.options.stream()
                            .map(option -> SingleOptionDialogInput.OptionEntry.create(option.id, option.display, option.initial))
                            .toList())
                    .width(clamp(input.width, 1, 1024))
                    .labelVisible(input.labelVisible)
                    .build();
        };
    }

    private ActionButton button(DialogView view, ConfigDialogButton button) {
        ActionButton.Builder builder = ActionButton.builder(button.label == null ? Component.empty() : button.label)
                .tooltip(button.tooltip)
                .width(clamp(button.width, 1, 1024));

        DialogActionHandler handler = view.action(button.actionId);
        if (handler != null) {
            builder.action(view.customAction(handler));
        } else if (button.commandTemplate != null && !button.commandTemplate.isBlank()) {
            builder.action(DialogAction.commandTemplate(button.commandTemplate));
        }
        return builder.build();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
