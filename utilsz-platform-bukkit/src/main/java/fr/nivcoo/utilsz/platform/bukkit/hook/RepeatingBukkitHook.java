package fr.nivcoo.utilsz.platform.bukkit.hook;

public abstract class RepeatingBukkitHook<C extends BukkitHookContext> implements BukkitHook<C>, Runnable {
    protected void onLoad(C context) {
    }

    protected abstract long periodTicks(C context);

    @Override
    public final void load(C context) {
        onLoad(context);
        long period = periodTicks(context);
        context.runRepeating(this, period, period);
    }
}
