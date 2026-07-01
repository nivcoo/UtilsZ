package fr.nivcoo.utilsz.platform.bukkit.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
public final class ItemBuilder {

    private final ItemStack stack;
    private boolean stripItalics = true;

    private ItemBuilder(ItemStack base) {
        this.stack = base;
    }

    public static ItemBuilder of(Material type) {
        return new ItemBuilder(new ItemStack(type, 1));
    }

    public static ItemBuilder of(Material type, int amount) {
        return new ItemBuilder(new ItemStack(type, Math.max(1, amount)));
    }

    public static ItemBuilder from(ItemStack base) {
        return new ItemBuilder(base == null ? new ItemStack(Material.AIR) : base.clone());
    }

    private ItemMeta meta() {
        ItemMeta m = stack.getItemMeta();
        if (m == null) m = Bukkit.getItemFactory().getItemMeta(stack.getType());
        return m;
    }

    private void setMeta(ItemMeta meta) {
        stack.setItemMeta(meta);
    }

    private Component style(Component c) {
        return stripItalics ? c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) : c;
    }

    public ItemStack build() {
        return stack;
    }

    public ItemBuilder italics(boolean enableItalics) {
        this.stripItalics = !enableItalics;
        return this;
    }

    public ItemBuilder preserveItalics() {
        this.stripItalics = false;
        return this;
    }

    public ItemBuilder noItalics() {
        this.stripItalics = true;
        return this;
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder name(Component c) {
        ItemMeta m = meta();
        m.displayName(style(c));
        setMeta(m);
        return this;
    }

    public ItemBuilder name(String legacy) {
        return name(LegacyComponentSerializer.legacySection().deserialize(legacy == null ? "" : legacy));
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        ItemMeta m = meta();
        if (lines != null) m.lore(lines.stream().map(this::style).toList());
        else m.lore(null);
        setMeta(m);
        return this;
    }

    public ItemBuilder lore(List<String> legacyLines) {
        ItemMeta m = meta();
        if (legacyLines == null || legacyLines.isEmpty()) {
            m.lore(null);
            setMeta(m);
            return this;
        }

        var sec = LegacyComponentSerializer.legacySection();
        var amp = LegacyComponentSerializer.legacyAmpersand();
        ArrayList<Component> out = new ArrayList<>(legacyLines.size());

        for (String s : legacyLines) {
            if (s == null) { out.add(style(Component.empty())); continue; }
            Component c = (s.indexOf('§') >= 0 ? sec : amp).deserialize(s);
            out.add(style(c));
        }

        m.lore(out);
        setMeta(m);
        return this;
    }

    public ItemBuilder addFlag(ItemFlag... flags) {
        ItemMeta m = meta();
        m.addItemFlags(flags);
        setMeta(m);
        return this;
    }

    public ItemBuilder hideAllFlags() {
        return addFlag(ItemFlag.values());
    }

    public ItemBuilder enchant(Enchantment e, int level) {
        if (e == null || level <= 0) return this;
        ItemMeta m = meta();
        m.addEnchant(e, level, true);
        setMeta(m);
        return this;
    }

    public ItemBuilder glow(boolean enabled) {
        if (!enabled) return this;
        addFlag(ItemFlag.HIDE_ENCHANTS);
        if (stack.getEnchantments().isEmpty()) {
            stack.addUnsafeEnchantment(Enchantment.LURE, 1);
        }
        return this;
    }

    public ItemBuilder unbreakable(boolean u) {
        ItemMeta m = meta();
        m.setUnbreakable(u);
        setMeta(m);
        return this;
    }

    public ItemBuilder damage(int dmg) {
        ItemMeta m = meta();
        if (m instanceof Damageable d) {
            d.setDamage(Math.max(0, dmg));
            setMeta(m);
        }
        return this;
    }

    public ItemBuilder customModelData(int data) {
        if (data <= 0) return this;
        ItemMeta m = meta();
        m.setCustomModelData(data);
        setMeta(m);
        return this;
    }

    public ItemBuilder leatherColor(Color c) {
        ItemMeta m = meta();
        if (m instanceof LeatherArmorMeta lam && c != null) {
            lam.setColor(c);
            setMeta(m);
        }
        return this;
    }

    public ItemBuilder texture(String base64) {
        if (stack.getType() != Material.PLAYER_HEAD || base64 == null || base64.isEmpty()) return this;

        SkullMeta m = (SkullMeta) meta();
        if (applyTextureProfile(m, base64)) setMeta(m);
        return this;
    }

    public ItemBuilder skullOwner(UUID owner) {
        if (stack.getType() != Material.PLAYER_HEAD || owner == null) return this;
        SkullMeta m = (SkullMeta) meta();
        m.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        setMeta(m);
        return this;
    }

    private static boolean applyTextureProfile(SkullMeta meta, String base64) {
        try {
            PlayerProfile profile = Bukkit.createProfile(
                    UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8)),
                    null
            );
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
