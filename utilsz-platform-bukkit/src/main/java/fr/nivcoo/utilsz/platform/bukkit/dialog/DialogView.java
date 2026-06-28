package fr.nivcoo.utilsz.platform.bukkit.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class DialogView {

    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, DialogActionHandler> actions = new HashMap<>();
    private final Player player;
    private final DialogProvider provider;

    DialogView(Player player, DialogProvider provider) {
        this.player = player;
        this.provider = provider;
    }

    public Player player() {
        return player;
    }

    public DialogProvider provider() {
        return provider;
    }

    public void put(String key, Object value) {
        values.put(key, value);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public void action(String id, DialogActionHandler handler) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        if (handler == null) actions.remove(id);
        else actions.put(id, handler);
    }

    public @Nullable DialogActionHandler action(String id) {
        return id == null ? null : actions.get(id);
    }

    public DialogAction customAction(DialogActionHandler handler) {
        return customAction(handler, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
    }

    public DialogAction customAction(DialogActionHandler handler, ClickCallback.Options options) {
        return DialogAction.customClick((response, audience) ->
                handler.handle(new DialogActionContext(this, response, audience)), options);
    }

    public ActionButton button(Component label, DialogActionHandler handler) {
        return ActionButton.builder(label).action(customAction(handler)).build();
    }
}
