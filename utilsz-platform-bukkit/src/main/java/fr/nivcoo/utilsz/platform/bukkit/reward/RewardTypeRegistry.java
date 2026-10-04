package fr.nivcoo.utilsz.platform.bukkit.reward;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.reward.type.CommandRewardType;
import fr.nivcoo.utilsz.platform.bukkit.reward.type.ExperienceRewardType;
import fr.nivcoo.utilsz.platform.bukkit.reward.type.ItemRewardType;
import fr.nivcoo.utilsz.platform.bukkit.reward.type.RewardType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class RewardTypeRegistry {
    private final Map<String, RewardType> types = new LinkedHashMap<>();

    public static RewardTypeRegistry builtins() {
        return builtins(1_000_000);
    }

    public static RewardTypeRegistry builtins(int maximumExperience) {
        return new RewardTypeRegistry().register(new ItemRewardType())
                .register(new CommandRewardType()).register(new ExperienceRewardType(maximumExperience));
    }

    public RewardTypeRegistry register(RewardType type) {
        Objects.requireNonNull(type, "type");
        String id = normalize(type.id());
        if (id.isEmpty()) throw new IllegalArgumentException("Reward type id cannot be blank.");
        if (types.putIfAbsent(id, type) != null)
            throw new IllegalArgumentException("Reward type already registered: " + id);
        return this;
    }

    public RewardAction compile(RewardDefinition definition, ConfigManager manager, Logger logger, String path) {
        if (definition == null || definition.type == null || definition.options == null)
            throw new IllegalArgumentException("Invalid reward: " + path + " requires type and options.");
        RewardCompileContext context = new RewardCompileContext(definition, manager, logger, path);
        RewardType type = types.get(normalize(definition.type));
        if (type == null) throw context.invalid("unknown type '" + definition.type + "'");
        RewardAction action = type.compile(context);
        if (action == null) throw context.invalid("type returned no action");
        return action;
    }

    public List<RewardAction> compile(List<? extends RewardDefinition> definitions, ConfigManager manager,
                                      Logger logger, String path) {
        if (definitions == null) throw new IllegalArgumentException("Invalid reward: " + path + " is required.");
        List<RewardAction> actions = new ArrayList<>(definitions.size());
        for (int index = 0; index < definitions.size(); index++)
            actions.add(compile(definitions.get(index), manager, logger, path + '[' + index + ']'));
        return List.copyOf(actions);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.strip().toUpperCase(Locale.ROOT);
    }
}
