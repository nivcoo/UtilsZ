package fr.nivcoo.utilsz.platform.bukkit.reward.type;

import fr.nivcoo.utilsz.platform.bukkit.reward.RewardAction;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardCompileContext;

@SuppressWarnings("unused")
public interface RewardType {
    String id();

    RewardAction compile(RewardCompileContext context);
}
