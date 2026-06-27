package fr.nivcoo.utilsz.platform.bukkit.hook;

public abstract class RepeatingBukkitHook<C extends BukkitHookContext> implements BukkitHook<C>, Runnable {
    protected abstract long periodTicks(C context);

    @Override
    public final void load(C context) {
        long period = periodTicks(context);
        context.runRepeating(this, period, period);
    }
}
