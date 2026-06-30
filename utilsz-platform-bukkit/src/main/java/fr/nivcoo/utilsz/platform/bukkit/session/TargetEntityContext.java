package fr.nivcoo.utilsz.platform.bukkit.session;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public record TargetEntityContext<T>(PlayerSessionManager manager, Player player, TargetSession<T> session,
                                     Entity entity) {
}
