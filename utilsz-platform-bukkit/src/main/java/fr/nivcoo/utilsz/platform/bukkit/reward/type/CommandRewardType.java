package fr.nivcoo.utilsz.platform.bukkit.reward.type;

import fr.nivcoo.utilsz.platform.bukkit.reward.RewardAction;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardCommands;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardCompileContext;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardStep;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public final class CommandRewardType implements RewardType {
    public static final BuiltinRewardType ID = BuiltinRewardType.COMMAND;

    @Override
    public RewardTypeId id() {
        return ID;
    }

    @Override
    public RewardAction compile(RewardCompileContext context) {
        context.requireOptions(Set.of("commands"));
        if (!(context.options().get("commands") instanceof List<?> source) || source.isEmpty())
            throw context.invalid("options.commands must be a non-empty list");
        List<String> commands = new ArrayList<>(source.size());
        for (Object value : source) {
            if (!(value instanceof String command)) throw context.invalid("options.commands must contain strings");
            commands.add(RewardCommands.normalize(command));
        }
        List<String> templates = List.copyOf(commands);
        return new RewardAction(ID, List.of(), execution -> {
            List<RewardStep> steps = new ArrayList<>(templates.size());
            for (String template : templates) {
                String command = RewardCommands.render(template, execution);
                steps.add(new RewardStep(() -> RewardCommands.registered(command)
                        ? RewardAction.CheckResult.ready()
                        : RewardAction.CheckResult.failure("Command is not registered: " + RewardCommands.root(command)),
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                                ? RewardAction.DeliveryResult.success()
                                : RewardAction.DeliveryResult.partialFailure("Command was not accepted: " + command)));
            }
            return steps;
        });
    }
}
