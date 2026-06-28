package fr.nivcoo.utilsz.platform.bukkit.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class DialogManager {

    private final Map<UUID, DialogView> views = new HashMap<>();

    public DialogView open(DialogProvider provider, Player player) {
        return open(provider, player, null);
    }

    public DialogView open(DialogProvider provider, Player player, Consumer<DialogView> params) {
        DialogView view = new DialogView(player, provider);
        if (params != null) params.accept(view);
        provider.init(view);
        views.put(player.getUniqueId(), view);
        player.showDialog(build(view));
        return view;
    }

    public DialogView get(Player player) {
        return get(player.getUniqueId());
    }

    public DialogView get(UUID uuid) {
        return views.get(uuid);
    }

    public Collection<DialogView> views() {
        return Collections.unmodifiableCollection(views.values());
    }

    public void close(Player player) {
        views.remove(player.getUniqueId());
        player.closeDialog();
    }

    public Dialog build(DialogView view) {
        DialogProvider provider = view.provider();
        DialogBase base = DialogBase.builder(provider.title(view))
                .externalTitle(provider.externalTitle(view))
                .canCloseWithEscape(provider.canCloseWithEscape(view))
                .pause(provider.pause(view))
                .afterAction(provider.afterAction(view))
                .body(provider.body(view))
                .inputs(provider.inputs(view))
                .build();

        var buttons = provider.buttons(view);
        var exitButton = provider.exitButton(view);
        var type = provider.type(view, buttons, exitButton);
        return Dialog.create(factory -> {
            DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(base).type(type);
        });
    }
}
