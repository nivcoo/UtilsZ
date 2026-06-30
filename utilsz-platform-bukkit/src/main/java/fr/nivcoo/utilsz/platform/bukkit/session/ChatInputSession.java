package fr.nivcoo.utilsz.platform.bukkit.session;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public record ChatInputSession<T>(T data, boolean allowEmpty,
                                  Consumer<ChatInputContext<T>> onInput,
                                  Consumer<ChatInputContext<T>> onInvalidInput,
                                  Consumer<SessionContext<T>> onCancel) implements PlayerSession<T> {

    public ChatInputSession {
        if (onInput == null) onInput = context -> {
        };
        if (onInvalidInput == null) onInvalidInput = context -> {
        };
        if (onCancel == null) onCancel = context -> {
        };
    }
}
