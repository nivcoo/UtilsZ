package fr.nivcoo.utilsz.platform.bukkit.session;

import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public record ChatInputContext<T>(PlayerSessionManager manager, Player player, ChatInputSession<T> session,
                                  String message) {
}
