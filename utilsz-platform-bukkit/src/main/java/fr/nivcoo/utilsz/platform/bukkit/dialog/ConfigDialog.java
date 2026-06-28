package fr.nivcoo.utilsz.platform.bukkit.dialog;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.config.annotations.Comment;
import io.papermc.paper.registry.data.dialog.DialogBase;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ConfigDialog {

    public ConfigDialogType type = ConfigDialogType.MULTI_ACTION;
    public Component title = ConfigManager.parseDynamic("&6Dialog");
    public Component externalTitle = null;
    public boolean canCloseWithEscape = true;
    public boolean pause = false;
    public DialogBase.DialogAfterAction afterAction = DialogBase.DialogAfterAction.CLOSE;
    public int columns = 1;
    public List<ConfigDialogBody> body = new ArrayList<>();
    public List<ConfigDialogInput> inputs = new ArrayList<>();
    public List<ConfigDialogButton> buttons = new ArrayList<>();
    @Comment("Bouton de sortie optionnel, surtout utile pour les dialogs multi_action.")
    public ConfigDialogButton exitButton = null;
}
