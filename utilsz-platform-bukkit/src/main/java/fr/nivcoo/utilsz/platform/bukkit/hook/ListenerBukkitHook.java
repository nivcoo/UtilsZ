package fr.nivcoo.utilsz.platform.bukkit.hook;

import org.bukkit.event.Listener;

public abstract class ListenerBukkitHook<C extends BukkitHookContext> implements BukkitHook<C>, Listener {
    @Override
    public final void load(C context) {
        context.registerListener(this);
    }
}
