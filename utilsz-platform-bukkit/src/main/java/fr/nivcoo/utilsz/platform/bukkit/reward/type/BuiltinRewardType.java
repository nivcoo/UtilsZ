package fr.nivcoo.utilsz.platform.bukkit.reward.type;

import java.util.function.IntFunction;

@SuppressWarnings("unused")
public enum BuiltinRewardType implements RewardTypeId {
    ITEM(ignored -> new ItemRewardType()),
    COMMAND(ignored -> new CommandRewardType()),
    EXP(maximum -> new ExperienceRewardType(maximum));

    private final IntFunction<RewardType> factory;

    BuiltinRewardType(IntFunction<RewardType> factory) {
        this.factory = factory;
    }

    public RewardType create(int maximumExperience) {
        return factory.apply(maximumExperience);
    }
}
