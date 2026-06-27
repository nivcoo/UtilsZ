package fr.nivcoo.utilsz.platform.velocity.hook;

import fr.nivcoo.utilsz.core.hook.Hook;
import fr.nivcoo.utilsz.core.hook.HookRegistry;

import java.util.List;
import java.util.function.Function;

public final class VelocityHookRegistry<C extends VelocityHookContext> {

    private final List<Function<C, Hook<C>>> hooks;

    public VelocityHookRegistry(List<Function<C, VelocityHook<C>>> hooks) {
        this.hooks = hooks.stream()
                .<Function<C, Hook<C>>>map(factory -> factory::apply)
                .toList();
    }

    public void loadAll(C context) {
        new HookRegistry<>(hooks, plugin -> context.proxy().getPluginManager().getPlugin(plugin).isPresent()).loadAll(context);
    }
}
