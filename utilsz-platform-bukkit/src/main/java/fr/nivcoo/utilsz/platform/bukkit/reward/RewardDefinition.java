package fr.nivcoo.utilsz.platform.bukkit.reward;

import fr.nivcoo.utilsz.core.config.annotations.Required;
import fr.nivcoo.utilsz.core.config.annotations.Section;

import java.util.LinkedHashMap;
import java.util.Map;

@Section
@SuppressWarnings("unused")
public class RewardDefinition {
    @Required
    public String type;
    @Required
    public Map<String, Object> options = new LinkedHashMap<>();

    public static RewardDefinition of(String type, Map<String, Object> options) {
        RewardDefinition definition = new RewardDefinition();
        definition.type = type;
        definition.options = new LinkedHashMap<>(options);
        return definition;
    }
}
