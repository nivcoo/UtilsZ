package fr.nivcoo.utilsz.platform.bukkit.hook;

import fr.nivcoo.utilsz.core.hook.Hook;
import fr.nivcoo.utilsz.core.hook.HookRegistry;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.function.Function;

public final class BukkitHookRegistry<C extends BukkitHookContext> {

    public BukkitHookRegistry(List<Function<C, BukkitHook<C>>> hooks) {
        this.registry = new HookRegistry<>(
                hooks.stream()
                        .<Function<C, Hook<C>>>map(factory -> factory::apply)
                        .toList(),
                plugin -> Bukkit.getPluginManager().isPluginEnabled(plugin)
        );
    }

    private final HookRegistry<C> registry;

    public void loadAll(C context) {
        registry.loadAll(context);
    }
}
