package fr.nivcoo.utilsz.platform.bukkit.reward.type;

import fr.nivcoo.utilsz.platform.bukkit.reward.RewardAction;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardCompileContext;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardStep;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardNumbers;

import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public final class ExperienceRewardType implements RewardType {
    public static final String ID = "EXP";
    private final int maximum;

    public ExperienceRewardType(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Maximum experience must be positive.");
        this.maximum = maximum;
    }

    @Override
    public String id() { return ID; }

    @Override
    public RewardAction compile(RewardCompileContext context) {
        context.requireOptions(Set.of("amount"));
        int amount = RewardNumbers.wholeInt(context.options().get("amount"), 1, maximum, context.path() + ".options.amount");
        return new RewardAction(ID, List.of(), execution -> {
            var player = execution.requirePlayer();
            return List.of(new RewardStep(RewardAction.CheckResult::ready, () -> {
                player.giveExp(amount, false);
                return RewardAction.DeliveryResult.success();
            }));
        });
    }
}
