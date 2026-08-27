package fr.nivcoo.utilsz.platform.bukkit.item;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.gui.ConfigGuiItem;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class ConfigItemFactory {

    private ConfigItemFactory() {
    }

    public static ItemStack create(ConfigItem def) {
        return create(def, (Logger) null);
    }

    public static ItemStack create(ConfigItem def, Logger logger) {
        if (def == null || def.material == null || def.material.isAir()) return null;

        ItemBuilder builder = ItemBuilder.of(def.material, amount(def))
                .texture(def.texture)
                .leatherColor(color(def.color, logger))
                .customModelData(def.customModelData);
        applySkullOwner(def, logger, builder);
        if (def.name != null) builder.name(def.name);
        if (def.lore != null && !def.lore.isEmpty()) builder.loreComponents(def.lore);
        if (Boolean.TRUE.equals(def.glow)) builder.glow(true);
        ItemStack stack = builder.build();

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        applyArmorTrim(def, meta, logger);

        for (ItemFlag flag : def.flags) {
            if (flag != null) meta.addItemFlags(flag);
        }

        boolean isBook = stack.getType() == Material.ENCHANTED_BOOK;
        if (def.enchants == null || def.enchants.isEmpty()) {
            stack.setItemMeta(meta);
            return stack;
        }

        for (Map.Entry<String, Integer> entry : def.enchants.entrySet()) {
            Enchantment enchantment = enchantment(entry.getKey());
            if (enchantment == null) {
                if (logger != null) logger.warning("Invalid enchantment: " + entry.getKey());
                continue;
            }

            int level = Math.max(1, entry.getValue());
            if (isBook && meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchantment, level, true);
            } else {
                meta.addEnchant(enchantment, level, true);
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    public static ConfigItem copy(ConfigItem def) {
        if (def == null) return null;
        ConfigItem copy = def instanceof ConfigGuiItem ? new ConfigGuiItem() : new ConfigItem();
        if (copy instanceof ConfigGuiItem copyGui && def instanceof ConfigGuiItem gui) {
            copyGui.enabled = gui.enabled;
            copyGui.slot = gui.slot;
            copyGui.slots = gui.slots == null ? List.of() : List.copyOf(gui.slots);
        }
        copy.material = def.material;
        copy.amount = def.amount;
        copy.texture = def.texture;
        copy.skullOwner = def.skullOwner;
        copy.name = def.name;
        copy.lore = def.lore == null ? List.of() : List.copyOf(def.lore);
        copy.enchants = def.enchants == null ? null : new LinkedHashMap<>(def.enchants);
        copy.flags = def.flags == null ? List.of() : List.copyOf(def.flags);
        copy.glow = def.glow;
        copy.color = def.color;
        copy.customModelData = def.customModelData;
        if (def.trim != null) {
            copy.trim = new ConfigItem.ArmorTrimConfig();
            copy.trim.material = def.trim.material;
            copy.trim.pattern = def.trim.pattern;
        }
        return copy;
    }

    public static ConfigItem format(ConfigItem def, Map<String, ?> placeholders) {
        ConfigItem copy = copy(def);
        if (copy == null) return null;
        Map<String, ?> safePlaceholders = placeholders == null ? Map.of() : placeholders;
        copy.name = ConfigManager.fmt(copy.name, safePlaceholders);
        copy.lore = copy.lore == null ? List.of() : copy.lore.stream()
                .map(line -> ConfigManager.fmt(line, safePlaceholders))
                .toList();
        return copy;
    }

    private static void applySkullOwner(ConfigItem def, Logger logger, ItemBuilder builder) {
        if (def.material != Material.PLAYER_HEAD) return;
        if (def.texture != null && !def.texture.isBlank()) return;
        if (def.skullOwner == null || def.skullOwner.isBlank()) return;

        try {
            builder.skullOwner(UUID.fromString(def.skullOwner));
        } catch (IllegalArgumentException e) {
            if (logger != null) logger.warning("Invalid skull_owner UUID: " + def.skullOwner);
        }
    }

    private static void applyArmorTrim(ConfigItem def, ItemMeta meta, Logger logger) {
        ConfigItem.ArmorTrimConfig trim = def.trim;
        if (trim == null) return;

        String materialName = trim.material == null ? "" : trim.material.trim();
        String patternName = trim.pattern == null ? "" : trim.pattern.trim();
        if (materialName.isBlank() && patternName.isBlank()) return;

        if (!(meta instanceof ArmorMeta armorMeta)) {
            if (logger != null) logger.warning("Armor trim configured for non-armor material: " + def.material);
            return;
        }

        TrimMaterial material = trimMaterial(materialName);
        TrimPattern pattern = trimPattern(patternName);
        if (material == null || pattern == null) {
            if (logger != null && material == null) {
                logger.warning("Invalid armor trim material: " + materialName);
            }
            if (logger != null && pattern == null) {
                logger.warning("Invalid armor trim pattern: " + patternName);
            }
            return;
        }

        armorMeta.setTrim(new ArmorTrim(material, pattern));
    }

    private static TrimMaterial trimMaterial(String configured) {
        NamespacedKey key = registryKey(configured);
        if (key == null) return null;
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(key);
    }

    private static TrimPattern trimPattern(String configured) {
        NamespacedKey key = registryKey(configured);
        if (key == null) return null;
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(key);
    }

    private static NamespacedKey registryKey(String configured) {
        if (configured == null || configured.isBlank()) return null;

        String value = configured.trim().toLowerCase(Locale.ROOT);
        return NamespacedKey.fromString(value);
    }

    public static Enchantment enchantment(String name) {
        if (name == null || name.isBlank()) return null;

        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase());
        if (key != null) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment != null) return enchantment;
        }

        key = NamespacedKey.minecraft(name.toLowerCase());
        Enchantment enchantment = Registry.ENCHANTMENT.get(key);
        if (enchantment != null) return enchantment;
        return Enchantment.getByName(name.toUpperCase());
    }

    static int amount(ConfigItem def) {
        return def == null ? 1 : Math.max(1, def.amount);
    }

    private static Color color(String configured, Logger logger) {
        if (configured == null || configured.isBlank()) return null;

        String value = configured.trim();
        try {
            if (value.startsWith("#") && value.length() == 7) {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            }

            String[] components = value.split(",");
            if (components.length != 3) throw new IllegalArgumentException();
            int red = Integer.parseInt(components[0].trim());
            int green = Integer.parseInt(components[1].trim());
            int blue = Integer.parseInt(components[2].trim());
            return Color.fromRGB(red, green, blue);
        } catch (IllegalArgumentException ignored) {
            if (logger != null) logger.warning("Invalid leather color: " + configured);
            return null;
        }
    }

}
