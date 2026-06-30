package fr.nivcoo.utilsz.platform.bukkit.session;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

@SuppressWarnings("unused")
public record TargetBlockContext<T>(PlayerSessionManager manager, Player player, TargetSession<T> session,
                                    Block block, Action action) {
}
