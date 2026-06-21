package fr.nivcoo.utilsz.platform.bukkit.conversion;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.conversion.Converter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemStackConv implements Converter<ItemStack> {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public ItemStack read(Object raw, ItemStack fallback, Field field) {
        if (raw == null) return fallback;
        if (raw instanceof ItemStack stack) return stack;
        if (!(raw instanceof Map<?, ?> map)) return fallback;

        Material material = Material.STONE;
        Object materialRaw = map.get("material");
        if (materialRaw != null) {
            Material resolved = resolveMaterial(String.valueOf(materialRaw));
            if (resolved != null) material = resolved;
        }

        ItemStack item = new ItemStack(material, parseInt(map.get("amount")));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Object name = map.get("name");
        if (name != null) {
            meta.displayName(noItalicIfAbsent(toComponent(name)));
        }

        Object loreObj = map.get("lore");
        if (loreObj instanceof List<?> list) {
            List<Component> lore = new ArrayList<>(list.size());
            for (Object line : list) lore.add(noItalicIfAbsent(toComponent(line)));
            meta.lore(lore);
        }

        if (Boolean.TRUE.equals(map.get("glow"))) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        Object enchObj = map.get("enchants");
        if (enchObj instanceof Map<?, ?> enchants) {
            if (meta instanceof EnchantmentStorageMeta book) {
                for (Map.Entry<?, ?> entry : enchants.entrySet()) {
                    Enchantment enchantment = resolveEnchant(String.valueOf(entry.getKey()));
                    if (enchantment != null) book.addStoredEnchant(enchantment, parseInt(entry.getValue()), true);
                }
            } else {
                for (Map.Entry<?, ?> entry : enchants.entrySet()) {
                    Enchantment enchantment = resolveEnchant(String.valueOf(entry.getKey()));
                    if (enchantment != null) meta.addEnchant(enchantment, parseInt(entry.getValue()), true);
                }
            }
        }

        Object flagsObj = map.get("flags");
        if (flagsObj instanceof List<?> flags) {
            for (Object flag : flags) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(String.valueOf(flag).toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                }
            }
        }

        Object customModelData = map.get("custom_model_data");
        if (customModelData != null) {
            try {
                meta.setCustomModelData(Integer.parseInt(String.valueOf(customModelData)));
            } catch (NumberFormatException ignored) {
            }
        }

        Object nbtObj = map.get("nbt");
        if (nbtObj instanceof Map<?, ?> nbt) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            for (Map.Entry<?, ?> entry : nbt.entrySet()) {
                NamespacedKey key = keyOf(String.valueOf(entry.getKey()));
                if (key != null) pdc.set(key, PersistentDataType.STRING, String.valueOf(entry.getValue()));
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Object write(ItemStack item, Field field) {
        if (item == null) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("material", item.getType().getKey().asString());
        if (item.getAmount() != 1) out.put("amount", item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return out;

        Component displayName = meta.displayName();
        if (displayName != null) out.put("name", MM.serialize(noItalicIfAbsent(displayName)));

        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            List<String> lines = new ArrayList<>(lore.size());
            for (Component line : lore) lines.add(MM.serialize(noItalicIfAbsent(line)));
            out.put("lore", lines);
        }

        if (!meta.getEnchants().isEmpty()) {
            Map<String, Integer> enchants = new LinkedHashMap<>();
            meta.getEnchants().forEach((enchantment, level) -> enchants.put(enchantment.getKey().asString(), level));
            out.put("enchants", enchants);
        }

        if (!meta.getItemFlags().isEmpty()) {
            List<String> flags = new ArrayList<>();
            for (ItemFlag flag : meta.getItemFlags()) flags.add(flag.name());
            out.put("flags", flags);
        }

        if (meta.hasCustomModelData()) {
            out.put("custom_model_data", meta.getCustomModelData());
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.isEmpty()) {
            Map<String, String> nbt = new LinkedHashMap<>();
            for (NamespacedKey key : pdc.getKeys()) {
                String value = pdc.get(key, PersistentDataType.STRING);
                if (value != null) nbt.put(key.asString(), value);
            }
            if (!nbt.isEmpty()) out.put("nbt", nbt);
        }

        return out;
    }

    private static Material resolveMaterial(String input) {
        String text = input.trim();
        NamespacedKey key = text.contains(":")
                ? NamespacedKey.fromString(text.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(text.toLowerCase(Locale.ROOT));
        if (key != null) {
            Material byKey = Registry.MATERIAL.get(key);
            if (byKey != null) return byKey;
        }
        try {
            return Material.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Enchantment resolveEnchant(String keyOrName) {
        String text = keyOrName.trim();
        NamespacedKey key = text.contains(":")
                ? NamespacedKey.fromString(text.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(text.toLowerCase(Locale.ROOT));
        if (key != null) {
            Enchantment byKey = Enchantment.getByKey(key);
            if (byKey != null) return byKey;
        }
        try {
            return Enchantment.getByName(text.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static NamespacedKey keyOf(String input) {
        String text = input.trim();
        return text.contains(":")
                ? NamespacedKey.fromString(text.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(text.toLowerCase(Locale.ROOT));
    }

    private static Component toComponent(Object value) {
        if (value instanceof Component component) return component;
        return ConfigManager.parseDynamic(String.valueOf(value));
    }

    private static Component noItalicIfAbsent(Component component) {
        if (component == null) return null;
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static int parseInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 1;
        }
    }
}
