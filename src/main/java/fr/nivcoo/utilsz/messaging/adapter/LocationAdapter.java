package fr.nivcoo.utilsz.messaging.adapter;

import com.google.gson.JsonObject;
import fr.nivcoo.utilsz.messaging.BusTypeAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class LocationAdapter implements BusTypeAdapter<Location> {

    @Override
    public JsonObject serialize(Location loc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", loc.getWorld().getName());
        obj.addProperty("x", loc.getX());
        obj.addProperty("y", loc.getY());
        obj.addProperty("z", loc.getZ());
        obj.addProperty("yaw", loc.getYaw());
        obj.addProperty("pitch", loc.getPitch());
        return obj;
    }

    @Override
    public Location deserialize(JsonObject json) {
        World world = Bukkit.getWorld(json.get("world").getAsString());
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        float yaw = json.has("yaw") ? json.get("yaw").getAsFloat() : 0f;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 0f;

        return new Location(world, x, y, z, yaw, pitch);
    }
}
