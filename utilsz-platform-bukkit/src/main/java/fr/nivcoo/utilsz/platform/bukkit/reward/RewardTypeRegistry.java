package fr.nivcoo.utilsz.platform.bukkit.reward;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.reward.type.BuiltinRewardType;
import fr.nivcoo.utilsz.platform.bukkit.reward.type.RewardType;
import fr.nivcoo.utilsz.platform.bukkit.reward.type.RewardTypeId;

import java.util.*;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class RewardTypeRegistry {
    private final Map<String, RewardType> types = new LinkedHashMap<>();

    public static RewardTypeRegistry builtins() {
        return builtins(1_000_000);
    }

    public static RewardTypeRegistry builtins(int maximumExperience) {
        RewardTypeRegistry registry = new RewardTypeRegistry();
        for (BuiltinRewardType type : BuiltinRewardType.values()) registry.register(type.create(maximumExperience));
        return registry;
    }

    public List<RewardTypeId> identifiers() {
        return types.values().stream().map(RewardType::id).toList();
    }

    public RewardTypeRegistry register(RewardType type) {
        Objects.requireNonNull(type, "type");
        String id = normalize(type.id().name());
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
        if (!type.id().equals(action.type())) throw context.invalid("type returned an action with another identifier");
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
