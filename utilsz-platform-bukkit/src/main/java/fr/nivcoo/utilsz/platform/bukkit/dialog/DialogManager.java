package fr.nivcoo.utilsz.platform.bukkit.dialog;

import io.papermc.paper.dialog.Dialog;
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
        Dialog dialog = build(view);
        UUID playerId = player.getUniqueId();
        DialogView previous = views.put(playerId, view);
        try {
            player.showDialog(dialog);
            return view;
        } catch (RuntimeException | Error error) {
            if (previous == null) {
                views.remove(playerId, view);
            } else {
                views.replace(playerId, view, previous);
            }
            throw error;
        }
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

    /**
     * Stops tracking a dialog whose remaining lifecycle is entirely
     * client-side, without changing the screen currently shown to the player.
     */
    public DialogView detach(Player player) {
        return views.remove(player.getUniqueId());
    }

    public Dialog build(DialogView view) {
        DialogProvider provider = view.provider();
        return Dialog.create(factory -> provider.configureBuilder(view, factory.empty()));
    }
}
