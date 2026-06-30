package fr.nivcoo.utilsz.platform.bukkit.session;

import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public record SessionContext<T>(PlayerSessionManager manager, Player player, PlayerSession<T> session) {
}
