package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
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

        ItemBuilder builder = ItemBuilder.of(def.material, Math.max(1, def.amount))
                .texture(def.texture)
                .customModelData(def.customModelData)
                .glow(def.glow);
        if (def.name != null) builder.name(def.name);
        if (def.lore != null && !def.lore.isEmpty()) builder.loreComponents(def.lore);
        ItemStack stack = builder.build();

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        for (ItemFlag flag : def.flags) {
            if (flag != null) meta.addItemFlags(flag);
        }

        boolean isBook = stack.getType() == Material.ENCHANTED_BOOK;
        for (Map.Entry<String, Integer> entry : def.enchantments.entrySet()) {
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
}
