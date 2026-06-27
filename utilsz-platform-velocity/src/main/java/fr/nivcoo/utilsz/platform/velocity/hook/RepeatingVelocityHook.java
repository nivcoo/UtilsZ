package fr.nivcoo.utilsz.platform.velocity.hook;

import java.time.Duration;

public abstract class RepeatingVelocityHook<C extends VelocityHookContext> implements VelocityHook<C>, Runnable {
    protected abstract Duration delay(C context);

    protected abstract Duration period(C context);

    @Override
    public final void load(C context) {
        context.runRepeating(this, delay(context), period(context));
    }
}
