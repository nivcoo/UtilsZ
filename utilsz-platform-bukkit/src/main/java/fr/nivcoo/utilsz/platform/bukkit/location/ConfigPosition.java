package fr.nivcoo.utilsz.platform.bukkit.location;

import org.bukkit.Location;

@SuppressWarnings("unused")
public class ConfigPosition {

    public double x;
    public double y;
    public double z;
    public Float yaw;
    public Float pitch;

    public ConfigPosition() {
    }

    public ConfigPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ConfigPosition(double x, double y, double z, Float yaw, Float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static ConfigPosition of(double x, double y, double z) {
        return new ConfigPosition(x, y, z);
    }

    public static ConfigPosition of(double x, double y, double z, float yaw, float pitch) {
        return new ConfigPosition(x, y, z, yaw, pitch);
    }

    public Location relativeTo(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            throw new IllegalArgumentException("Origin and world cannot be null.");
        }
        Location location = origin.clone().add(x, y, z);
        if (yaw != null) location.setYaw(yaw);
        if (pitch != null) location.setPitch(pitch);
        return location;
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public boolean hasRotation() {
        return yaw != null || pitch != null;
    }
}
