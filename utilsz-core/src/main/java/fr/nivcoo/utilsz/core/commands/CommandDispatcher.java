package fr.nivcoo.utilsz.core.commands;

import java.util.List;

public interface CommandDispatcher {
    boolean dispatch(Sender sender, String label, String[] args);
    List<String> tabComplete(Sender sender, String label, String[] args);
}
