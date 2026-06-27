package fr.nivcoo.utilsz.core.hook;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class HookRegistry<C extends HookContext> {

    private final List<Function<C, Hook<C>>> hooks;
    private final Predicate<String> pluginAvailable;

    public HookRegistry(List<Function<C, Hook<C>>> hooks, Predicate<String> pluginAvailable) {
        this.hooks = List.copyOf(hooks);
        this.pluginAvailable = pluginAvailable == null ? ignored -> true : pluginAvailable;
    }

    public void loadAll(C context) {
        for (Function<C, Hook<C>> factory : hooks) {
            Hook<C> hook = factory.apply(context);
            if (!hook.enabled(context)) continue;
            String requiredPlugin = hook.requiredPlugin();
            if (requiredPlugin != null && !requiredPlugin.isBlank() && !pluginAvailable.test(requiredPlugin)) continue;
            load(context, hook);
        }
    }

    private void load(C context, Hook<C> hook) {
        try {
            hook.load(context);
            context.logHookInfo("Hook " + hook.id() + " enabled.");
        } catch (Throwable throwable) {
            context.logHookWarning("Hook " + hook.id() + " disabled: " + throwable.getMessage());
        }
    }
}
