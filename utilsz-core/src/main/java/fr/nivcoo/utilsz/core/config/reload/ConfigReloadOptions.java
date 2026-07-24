package fr.nivcoo.utilsz.core.config.reload;

public record ConfigReloadOptions(long pollTicks, int stablePolls, long retryTicks) {

    public static final ConfigReloadOptions DEFAULT = new ConfigReloadOptions(20L, 2, 600L);

    public ConfigReloadOptions {
        if (pollTicks < 1L) throw new IllegalArgumentException("pollTicks must be positive");
        if (stablePolls < 1) throw new IllegalArgumentException("stablePolls must be positive");
        if (retryTicks < 1L) throw new IllegalArgumentException("retryTicks must be positive");
    }
}
