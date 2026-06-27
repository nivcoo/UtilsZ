package fr.nivcoo.utilsz.platform.velocity.hook;

public abstract class ListenerVelocityHook<C extends VelocityHookContext> implements VelocityHook<C> {
    @Override
    public final void load(C context) {
        context.registerListener(this);
    }
}
