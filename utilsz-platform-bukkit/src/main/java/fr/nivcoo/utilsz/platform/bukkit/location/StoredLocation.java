package fr.nivcoo.utilsz.platform.bukkit.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public record StoredLocation(String worldName, double x, double y, double z, float yaw, float pitch) {

    public static StoredLocation from(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and world cannot be null.");
        }
        return new StoredLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public static StoredLocation fromBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and world cannot be null.");
        }
        return fromBlock(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static StoredLocation fromBlock(String worldName, int x, int y, int z) {
        return new StoredLocation(worldName, x, y, z, 0.0f, 0.0f);
    }

    public static StoredLocation parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("StoredLocation input cannot be blank.");
        }

        String[] parts = input.split(";");
        if (parts.length != 4 && parts.length != 6) {
            throw new IllegalArgumentException("Invalid StoredLocation: " + input);
        }

        return new StoredLocation(
                parts[0],
                parseDouble(parts[1]),
                parseDouble(parts[2]),
                parseDouble(parts[3]),
                parts.length == 6 ? parseFloat(parts[4]) : 0.0f,
                parts.length == 6 ? parseFloat(parts[5]) : 0.0f
        );
    }

    public static StoredLocation fromString(String input) {
        return parse(input);
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.trim().replace(',', '.'));
    }

    private static float parseFloat(String value) {
        return Float.parseFloat(value.trim().replace(',', '.'));
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public Block toBlock() {
        Location location = toLocation();
        return location == null ? null : location.getBlock();
    }

    public int getBlockX() {
        return (int) Math.floor(x);
    }

    public int getBlockY() {
        return (int) Math.floor(y);
    }

    public int getBlockZ() {
        return (int) Math.floor(z);
    }

    public String serialize() {
        if (isBlockAligned()) return serializeBlock();
        return worldName + ";"
                + x + ";"
                + y + ";"
                + z + ";"
                + yaw + ";"
                + pitch;
    }

    public String serializeBlock() {
        return worldName + ";" + getBlockX() + ";" + getBlockY() + ";" + getBlockZ();
    }

    public boolean isBlockAligned() {
        return Double.compare(x, getBlockX()) == 0
                && Double.compare(y, getBlockY()) == 0
                && Double.compare(z, getBlockZ()) == 0
                && Float.compare(yaw, 0.0f) == 0
                && Float.compare(pitch, 0.0f) == 0;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
