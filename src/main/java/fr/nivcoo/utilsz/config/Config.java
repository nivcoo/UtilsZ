package fr.nivcoo.utilsz.config;

import fr.nivcoo.utilsz.version.ServerVersion;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Config {
    private File fichierConfig;
    private FileConfiguration fconfig;

    public String translateHexColorCodes(String message) {
        String msg = message;
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(msg);
        while (matcher.find()) {
            String color = msg.substring(matcher.start(), matcher.end());
            msg = msg.replace(color, ChatColor.of(color) + "");
            matcher = pattern.matcher(msg);

        }
        return msg;
    }

    public String translate(String message) {
        String msg = message;
        if (ServerVersion.isServerVersionAtLeast(ServerVersion.V1_16)) {
            msg = translateHexColorCodes(msg);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * public Config: This method allow you to interact with an yml file.
     *
     * @param file Yml file who you want to interact.
     */
    public Config(File file) {
        this.fichierConfig = file;
        loadConfig();
    }

    /**
     * public void save: This method will save the yml file.
     */
    public void save() {
        try {
            fconfig.save(fichierConfig);
        } catch (IOException ex) {
            Bukkit.getLogger().severe("An error has occured while saving file " + fichierConfig.getPath());
        }
    }

    /**
     * public void loadConfig: This method will load the yml file.
     */
    public void loadConfig() {
        fconfig = YamlConfiguration.loadConfiguration(fichierConfig);
    }

    /**
     * public void set: This method will set an object into the yml file.
     *
     * @param path The path location where you would save the object.
     * @param obj  The object who you would save.
     */
    public void set(String path, Object obj) {
        if (obj instanceof Location) {
            Location loc = (Location) obj;
            fconfig.set(path + ".x", loc.getX());
            fconfig.set(path + ".y", loc.getY());
            fconfig.set(path + ".z", loc.getZ());
            fconfig.set(path + ".yaw", loc.getYaw());
            fconfig.set(path + ".pitch", loc.getPitch());
            fconfig.set(path + ".world", loc.getWorld().getName());
        } else
            fconfig.set(path, obj);
        save();
    }

    /**
     * public String getString: This method will return the String value.
     *
     * @param path The path location where you would get the String.
     */
    public String getString(String path, String... lists) {

        String name = fconfig.getString(path);
        if (name != null) {
            if (lists != null) {
                for (int i = 0; i < lists.length; i++) {
                    name = name.replace("{" + i + "}", lists[i]).replace("{prefix}", lists[i]);

                }
            }
        }

        return name == null ? null : translate(name);
    }

    /**
     * public int getInt: This method will return the Integer value.
     *
     * @param path The path location where you would get the Integer.
     */
    public int getInt(String path) {
        return fconfig.getInt(path);
    }

    /**
     * public long getLong: This method will return the Long value.
     *
     * @param path The path location where you would get the Long.
     */
    public long getLong(String path) {
        return fconfig.getLong(path);
    }

    /**
     * public boolean getBoolean: This method will return the Boolean value.
     *
     * @param path The path location where you would get the Boolean.
     */
    public boolean getBoolean(String path) {
        return fconfig.getBoolean(path);
    }

    /**
     * public double getDouble: This method will return the Double value.
     *
     * @param path The path location where you would get the Double.
     */
    public double getDouble(String path) {
        return fconfig.getDouble(path);
    }

    /**
     * public List<String> getStringList: This method will return a list of String.
     *
     * @param path The path location where you would get the StringList.
     */
    public List<String> getStringList(String path) {
        List<String> name = new ArrayList<>();
        for (String n : fconfig.getStringList(path)) {
            name.add(translate(n));
        }
        return name;
    }

    /**
     * public List<Integer> getIntegerList: This method will return a list of
     * Integer.
     *
     * @param path The path location where you would get the IntegerList.
     */
    public List<Integer> getIntegerList(String path) {
        return new ArrayList<>(fconfig.getIntegerList(path));
    }

    /**
     * public List<String> getKeys: This method will return a list of Keys.
     *
     * @param path The path location where you would get the KeysList.
     */
    public List<String> getKeys(String path) {
        List<String> list = new ArrayList<>();
        if ("".equalsIgnoreCase(path)) {
            list.addAll(fconfig.getKeys(false));
        } else {
            ConfigurationSection cs = fconfig.getConfigurationSection(path);
            if (cs != null)
                list.addAll(cs.getKeys(false));
        }
        return list;
    }

    public Location getLocation(String path) {
        double x = fconfig.getDouble(path + ".x");
        double y = fconfig.getDouble(path + ".y");
        double z = fconfig.getDouble(path + ".z");
        float yaw = (float) fconfig.getDouble(path + ".yaw");
        float pitch = (float) fconfig.getDouble(path + ".pitch");
        String world = fconfig.getString(path + ".world");
        if (world == null || "".equalsIgnoreCase(world))
            return null;
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    /**
     * public boolean exist: This method will return true if the path exist, however
     * false.
     *
     * @param path The location where you would see if path exist.
     */
    public boolean exist(String path) {
        return fconfig.contains(path);
    }

    public ItemStack getItem(String path) {
        return fconfig.getItemStack(path);
    }

    public void reload() {
        loadConfig();
    }

    public File getFile() {
        return fichierConfig;
    }
}