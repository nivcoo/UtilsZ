package fr.nivcoo.utilsz.platform.bukkit.version;

import org.bukkit.Bukkit;

public enum ServerVersion {

    UNKNOWN, V1_7, V1_8, V1_9, V1_10, V1_11, V1_12, V1_13, V1_14, V1_15, V1_16, V1_17, V1_18, V1_19, V1_20, V1_21, V1_22, V1_23, V1_24, V1_25;

    private static final ServerVersion serverVersion = detectVersion();

    private static ServerVersion detectVersion() {
        String version = Bukkit.getMinecraftVersion(); // Ex: "1.21"
        String[] parts = version.split("\\.");

        if (parts.length >= 2) {
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                String versionKey = "V" + major + "_" + minor;
                for (ServerVersion sv : values()) {
                    if (sv.name().equals(versionKey)) {
                        return sv;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
        return UNKNOWN;
    }

    public static ServerVersion getServerVersion() {
        return serverVersion;
    }

    public static boolean isServerVersion(ServerVersion version) {
        return serverVersion == version;
    }

    public static boolean isServerVersion(ServerVersion... versions) {
        for (ServerVersion version : versions) {
            if (serverVersion == version) {
                return true;
            }
        }
        return false;
    }

    public static boolean isServerVersionAbove(ServerVersion version) {
        return serverVersion.ordinal() > version.ordinal();
    }

    public static boolean isServerVersionAtLeast(ServerVersion version) {
        return serverVersion.ordinal() >= version.ordinal();
    }

    public static boolean isServerVersionAtOrBelow(ServerVersion version) {
        return serverVersion.ordinal() <= version.ordinal();
    }

    public static boolean isServerVersionBelow(ServerVersion version) {
        return serverVersion.ordinal() < version.ordinal();
    }
}
