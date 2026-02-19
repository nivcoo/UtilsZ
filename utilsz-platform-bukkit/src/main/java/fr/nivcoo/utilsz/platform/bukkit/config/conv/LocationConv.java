package fr.nivcoo.utilsz.platform.bukkit.config.conv;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class LocationConv implements Converter<Location> {

    @Override
    public Location read(Object raw, Location fallback, Field f) {
        switch (raw) {
            case null -> {
                return fallback;
            }

            case Location loc -> {
                return loc;
            }

            case Map<?, ?> map -> {
                World world = resolveWorld(map.get("world"), fallback != null ? fallback.getWorld() : null);
                if (world == null) return fallback;

                double x = d(map.get("x"), fallback != null ? fallback.getX() : 0);
                double y = d(map.get("y"), fallback != null ? fallback.getY() : 0);
                double z = d(map.get("z"), fallback != null ? fallback.getZ() : 0);
                float yaw = (float) d(map.get("yaw"), fallback != null ? fallback.getYaw() : 0);
                float pitch = (float) d(map.get("pitch"), fallback != null ? fallback.getPitch() : 0);

                return new Location(world, x, y, z, yaw, pitch);
            }

            case String s -> {
                String[] parts = s.trim().split(":");
                if (parts.length < 4) return fallback;

                World world = resolveWorld(parts[0], fallback != null ? fallback.getWorld() : null);
                if (world == null) return fallback;

                try {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : (fallback != null ? fallback.getYaw() : 0f);
                    float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : (fallback != null ? fallback.getPitch() : 0f);
                    return new Location(world, x, y, z, yaw, pitch);
                } catch (NumberFormatException ignore) {
                    return fallback;
                }
            }
            default -> {
            }
        }

        return fallback;
    }

    @Override
    public Object write(Location loc, Field f) {
        if (loc == null) return null;
        Map<String, Object> out = new LinkedHashMap<>(6);
        World w = loc.getWorld();
        out.put("world", w != null ? w.getName() : "world");
        out.put("x", loc.getX());
        out.put("y", loc.getY());
        out.put("z", loc.getZ());
        out.put("yaw", loc.getYaw());
        out.put("pitch", loc.getPitch());
        return out;
    }

    private static World resolveWorld(Object id, World def) {
        if (id == null) return def;
        String s = id.toString().trim();
        try { return Bukkit.getWorld(UUID.fromString(s)); } catch (IllegalArgumentException ignore) { }
        World byName = Bukkit.getWorld(s);
        return byName != null ? byName : def;
    }

    private static double d(Object o, double def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return def; }
    }
}
