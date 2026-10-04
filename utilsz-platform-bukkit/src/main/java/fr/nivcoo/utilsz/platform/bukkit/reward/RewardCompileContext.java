package fr.nivcoo.utilsz.platform.bukkit.reward;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItem;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItemFactory;
import fr.nivcoo.utilsz.platform.bukkit.item.ItemDelivery;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public record RewardCompileContext(RewardDefinition definition, ConfigManager configManager,
                                   Logger logger, String path) {
    private static final Set<String> ITEM_KEYS = Set.of("material", "amount", "texture", "skull_owner",
            "name", "lore", "enchants", "flags", "glow", "color", "custom_model_data", "trim");
    private static final Pattern ENCHANTMENT = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");

    public RewardCompileContext {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(configManager, "configManager");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(path, "path");
    }

    public Map<String, Object> options() {
        return definition.options;
    }

    public void requireOptions(Set<String> accepted) {
        if (options() == null) throw invalid("options is required");
        var unknown = options().keySet().stream().filter(key -> !accepted.contains(key)).sorted().toList();
        if (!unknown.isEmpty()) throw invalid("unknown options " + unknown);
    }

    public ConfigItem decodeItem(Object source) {
        ConfigItem item;
        if (source instanceof ConfigItem configured) {
            item = ConfigItemFactory.copy(configured);
        } else if (source instanceof Map<?, ?> raw) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !ITEM_KEYS.contains(key))
                    throw invalid("unknown item option " + entry.getKey());
                values.put(key, entry.getValue());
            }
            if (!values.containsKey("material")) throw invalid("item.material is required");
            if (values.containsKey("amount"))
                values.put("amount", RewardNumbers.wholeInt(values.get("amount"), 1,
                        ItemDelivery.MAX_DELIVERY_AMOUNT, path + ".options.items.amount"));
            item = configManager.decode(values, ConfigItem.class);
        } else {
            throw invalid("items must contain item definitions");
        }
        if (item == null || item.material == null || item.material == Material.AIR
                || item.material == Material.CAVE_AIR || item.material == Material.VOID_AIR)
            throw invalid("invalid item material");
        if (item.amount < 1 || item.amount > ItemDelivery.MAX_DELIVERY_AMOUNT)
            throw invalid("item amount must be between 1 and " + ItemDelivery.MAX_DELIVERY_AMOUNT);
        if (item.customModelData < 0 || item.lore != null && item.lore.stream().anyMatch(Objects::isNull)
                || item.flags != null && item.flags.stream().anyMatch(Objects::isNull))
            throw invalid("invalid item metadata");
        if (item.enchants != null && item.enchants.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || !ENCHANTMENT.matcher(entry.getKey()).matches() || entry.getValue() == null || entry.getValue() < 1))
            throw invalid("invalid item enchantment");
        return item;
    }

    public IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid reward: " + path + ' ' + reason + '.');
    }
}
