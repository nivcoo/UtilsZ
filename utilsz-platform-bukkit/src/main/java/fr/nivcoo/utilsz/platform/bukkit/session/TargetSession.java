package fr.nivcoo.utilsz.platform.bukkit.session;

import org.bukkit.Location;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public record TargetSession<T>(T data, Location origin, double maxDistance,
                               Function<TargetBlockContext<T>, Boolean> onBlockClick,
                               Function<TargetEntityContext<T>, Boolean> onEntityClick,
                               Consumer<SessionContext<T>> onInvalidTarget,
                               Consumer<SessionContext<T>> onCancel,
                               Consumer<SessionContext<T>> onWorldChanged,
                               Consumer<SessionContext<T>> onTooFar) implements PlayerSession<T> {

    public TargetSession {
        if (onBlockClick == null) onBlockClick = context -> false;
        if (onEntityClick == null) onEntityClick = context -> false;
        if (onInvalidTarget == null) onInvalidTarget = context -> {
        };
        if (onCancel == null) onCancel = context -> {
        };
        if (onWorldChanged == null) onWorldChanged = onCancel;
        if (onTooFar == null) onTooFar = onCancel;
        if (maxDistance < 0) maxDistance = 0;
    }
}
