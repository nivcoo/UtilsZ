package fr.nivcoo.utilsz.config.conv;

import fr.nivcoo.utilsz.config.ConfigManager;
import fr.nivcoo.utilsz.config.annotations.Converter;
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
import java.util.*;

public final class ItemStackConv implements Converter<ItemStack> {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public ItemStack read(Object raw, ItemStack fallback, Field f) {
        if (raw == null) return fallback;
        if (raw instanceof ItemStack s) return s;
        if (!(raw instanceof Map<?, ?> map)) return fallback;

        Material mat = Material.STONE;
        Object matRaw = map.get("material");
        if (matRaw != null) {
            Material m = resolveMaterial(String.valueOf(matRaw));
            if (m != null) mat = m;
        }

        int amount = parseInt(map.get("amount"));
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Object name = map.get("name");
        if (name != null) {
            Component c = toComponent(name);
            meta.displayName(noItalicIfAbsent(c));
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
        if (enchObj instanceof Map<?, ?> enchMap) {
            if (meta instanceof EnchantmentStorageMeta book) {
                for (var e : enchMap.entrySet()) {
                    Enchantment ench = resolveEnchant(String.valueOf(e.getKey()));
                    if (ench == null) continue;
                    book.addStoredEnchant(ench, parseInt(e.getValue()), true);
                }
            } else {
                for (var e : enchMap.entrySet()) {
                    Enchantment ench = resolveEnchant(String.valueOf(e.getKey()));
                    if (ench == null) continue;
                    meta.addEnchant(ench, parseInt(e.getValue()), true);
                }
            }
        }

        Object flagsObj = map.get("flags");
        if (flagsObj instanceof List<?> flags) {
            for (Object fo : flags) {
                try { meta.addItemFlags(ItemFlag.valueOf(String.valueOf(fo).toUpperCase(Locale.ROOT))); }
                catch (Exception ignored) {}
            }
        }

        Object cmd = map.get("custom_model_data");
        if (cmd != null) {
            try { meta.setCustomModelData(Integer.parseInt(String.valueOf(cmd))); }
            catch (NumberFormatException ignored) {}
        }

        Object nbtObj = map.get("nbt");
        if (nbtObj instanceof Map<?, ?> nbt) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            for (var e : nbt.entrySet()) {
                NamespacedKey k = keyOf(String.valueOf(e.getKey()));
                if (k != null) pdc.set(k, PersistentDataType.STRING, String.valueOf(e.getValue()));
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Object write(ItemStack item, Field f) {
        if (item == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("material", item.getType().getKey().asString());
        if (item.getAmount() != 1) out.put("amount", item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component dn = meta.displayName();
            if (dn != null) out.put("name", MM.serialize(noItalicIfAbsent(dn)));

            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                List<String> lines = new ArrayList<>(lore.size());
                for (Component c : lore) lines.add(MM.serialize(noItalicIfAbsent(c)));
                out.put("lore", lines);
            }

            if (!meta.getEnchants().isEmpty()) {
                Map<String, Integer> ench = new LinkedHashMap<>();
                meta.getEnchants().forEach((en, lvl) -> ench.put(en.getKey().asString(), lvl));
                out.put("enchants", ench);
            }

            if (!meta.getItemFlags().isEmpty()) {
                List<String> flags = new ArrayList<>();
                for (var fl : meta.getItemFlags()) flags.add(fl.name());
                out.put("flags", flags);
            }

            if (meta.hasCustomModelData())
                out.put("custom_model_data", meta.getCustomModelData());

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!pdc.isEmpty()) {
                Map<String, String> nbt = new LinkedHashMap<>();
                for (NamespacedKey k : pdc.getKeys()) {
                    String val = pdc.get(k, PersistentDataType.STRING);
                    if (val != null) nbt.put(k.asString(), val);
                }
                if (!nbt.isEmpty()) out.put("nbt", nbt);
            }
        }

        return out;
    }

    private static Material resolveMaterial(String in) {
        String s = in.trim();
        NamespacedKey k = s.contains(":")
                ? NamespacedKey.fromString(s.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT));
        if (k != null) {
            Material byKey = Registry.MATERIAL.get(k);
            if (byKey != null) return byKey;
        }
        try { return Material.valueOf(s.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        return null;
    }

    private static Enchantment resolveEnchant(String keyOrName) {
        String s = keyOrName.trim();
        NamespacedKey k = s.contains(":")
                ? NamespacedKey.fromString(s.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT));
        if (k != null) {
            Enchantment byKey = Enchantment.getByKey(k);
            if (byKey != null) return byKey;
        }
        try { return Enchantment.getByName(s.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        return null;
    }

    private static NamespacedKey keyOf(String s) {
        String in = s.trim();
        return in.contains(":") ? NamespacedKey.fromString(in.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(in.toLowerCase(Locale.ROOT));
    }

    private static Component toComponent(Object o) {
        if (o instanceof Component c) return c;
        return ConfigManager.parseDynamic(String.valueOf(o));
    }

    private static Component noItalicIfAbsent(Component c) {
        if (c == null) return null;
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static int parseInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 1; }
    }
}
