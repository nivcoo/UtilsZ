package fr.nivcoo.utilsz.platform.bukkit.dialog;

import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public record DialogActionContext(
        DialogView view,
        @Nullable DialogResponseView response,
        Audience audience
) {

    public Player player() {
        return view.player();
    }

    public @Nullable String text(String key) {
        return response == null ? null : response.getText(key);
    }

    public @Nullable Boolean bool(String key) {
        return response == null ? null : response.getBoolean(key);
    }

    public @Nullable Float number(String key) {
        return response == null ? null : response.getFloat(key);
    }
}
