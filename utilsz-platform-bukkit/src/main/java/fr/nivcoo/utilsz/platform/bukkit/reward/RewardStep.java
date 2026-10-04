package fr.nivcoo.utilsz.platform.bukkit.reward;

import java.util.Objects;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public record RewardStep(Supplier<RewardAction.CheckResult> preflight,
                         Supplier<RewardAction.DeliveryResult> delivery) {
    public RewardStep {
        Objects.requireNonNull(preflight, "preflight");
        Objects.requireNonNull(delivery, "delivery");
    }
}
