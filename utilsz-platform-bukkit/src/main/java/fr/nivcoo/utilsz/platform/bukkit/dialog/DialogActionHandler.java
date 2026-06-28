package fr.nivcoo.utilsz.platform.bukkit.dialog;

@FunctionalInterface
public interface DialogActionHandler {

    void handle(DialogActionContext context);
}
