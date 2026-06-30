package fr.nivcoo.utilsz.platform.bukkit.hook;

import org.bukkit.event.Listener;

public abstract class ListenerBukkitHook<C extends BukkitHookContext> implements BukkitHook<C>, Listener {
    protected void onLoad(C context) {
    }

    @Override
    public final void load(C context) {
        onLoad(context);
        context.registerListener(this);
    }
}
