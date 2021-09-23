package fr.nivcoo.utilsz.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public interface Command {

    /**
     * Inspired by BG-Software-LLC (https://bg-software.com/)
     * Get the aliases of the sub command.
     */
    List<String> getAliases();

    String getPermission();

    String getUsage();

    String getDescription();

    int getMinArgs();

    int getMaxArgs();

    boolean canBeExecutedByConsole();

    void execute(JavaPlugin plugin, CommandSender sender, String[] args);

    List<String> tabComplete(JavaPlugin plugin, CommandSender sender, String[] args);

}