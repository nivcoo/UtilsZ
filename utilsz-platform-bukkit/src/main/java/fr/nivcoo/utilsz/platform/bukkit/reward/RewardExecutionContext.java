package fr.nivcoo.utilsz.platform.bukkit.reward;

import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("unused")
public record RewardExecutionContext(UUID playerId, Player player, Map<String, String> variables) {
    public RewardExecutionContext {
        Objects.requireNonNull(playerId, "playerId");
        if (player != null && !playerId.equals(player.getUniqueId()))
            throw new IllegalArgumentException("Reward player and UUID do not match.");
        variables = Map.copyOf(variables);
    }

    public static RewardExecutionContext of(Player player) {
        return new RewardExecutionContext(player.getUniqueId(), player, Map.of());
    }

    public static RewardExecutionContext offline(UUID playerId) {
        return new RewardExecutionContext(playerId, null, Map.of());
    }

    public RewardExecutionContext withVariables(Map<String, String> values) {
        Map<String, String> merged = new LinkedHashMap<>(variables);
        merged.putAll(values);
        return new RewardExecutionContext(playerId, player, merged);
    }

    public Player requirePlayer() {
        if (player == null) throw new IllegalStateException("This reward requires an online player.");
        return player;
    }
}
