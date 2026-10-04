package fr.nivcoo.utilsz.platform.bukkit.reward;

import fr.nivcoo.utilsz.platform.bukkit.reward.type.RewardTypeId;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class RewardAction {
    private final RewardTypeId type;
    private final List<Supplier<ItemStack>> displaySuppliers;
    private final Function<RewardExecutionContext, List<RewardStep>> preparation;

    public RewardAction(RewardTypeId type, List<Supplier<ItemStack>> displaySuppliers,
                        Function<RewardExecutionContext, List<RewardStep>> preparation) {
        this.type = Objects.requireNonNull(type, "type");
        this.displaySuppliers = List.copyOf(displaySuppliers);
        this.preparation = Objects.requireNonNull(preparation, "preparation");
    }

    public RewardAction(RewardTypeId type, List<Supplier<ItemStack>> displaySuppliers,
                        Function<RewardExecutionContext, CheckResult> preflight,
                        Function<RewardExecutionContext, DeliveryResult> delivery) {
        this(type, displaySuppliers, context -> List.of(new RewardStep(
                () -> preflight.apply(context), () -> delivery.apply(context))));
        Objects.requireNonNull(preflight, "preflight");
        Objects.requireNonNull(delivery, "delivery");
    }

    public RewardTypeId type() {
        return type;
    }

    public List<Supplier<ItemStack>> displaySuppliers() {
        return displaySuppliers;
    }

    public List<RewardStep> prepare(RewardExecutionContext context) {
        return List.copyOf(preparation.apply(context));
    }

    public static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public record CheckResult(boolean allowed, String reason) {
        public CheckResult {
            reason = reason == null ? "" : reason;
        }

        public static CheckResult ready() {
            return new CheckResult(true, "");
        }

        public static CheckResult failure(String reason) {
            return new CheckResult(false, reason);
        }
    }

    public record DeliveryResult(boolean delivered, boolean partial, String reason) {
        public DeliveryResult {
            partial = !delivered && partial;
            reason = reason == null ? "" : reason;
        }

        public static DeliveryResult success() {
            return new DeliveryResult(true, false, "");
        }

        public static DeliveryResult failure(String reason) {
            return new DeliveryResult(false, false, reason);
        }

        public static DeliveryResult partialFailure(String reason) {
            return new DeliveryResult(false, true, reason);
        }
    }
}
