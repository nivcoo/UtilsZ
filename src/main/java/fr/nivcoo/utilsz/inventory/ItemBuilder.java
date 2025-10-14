package fr.nivcoo.utilsz.inventory;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ItemBuilder {

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
        stack.setItemMeta(m);
        return this;
    }

    public ItemBuilder name(String legacy) {
        return name(LegacyComponentSerializer.legacySection().deserialize(legacy == null ? "" : legacy));
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        ItemMeta m = meta();
        if (lines != null) {
            m.lore(lines.stream().map(this::style).toList());
        } else {
            m.lore(null);
        }
        stack.setItemMeta(m);
        return this;
    }

    public ItemBuilder lore(List<String> legacyLines) {
        ItemMeta m = meta();
        if (legacyLines == null || legacyLines.isEmpty()) {
            m.lore(null);
            stack.setItemMeta(m);
            return this;
        }

        var sec = LegacyComponentSerializer.legacySection();
        var amp = LegacyComponentSerializer.legacyAmpersand();
        java.util.ArrayList<Component> out = new java.util.ArrayList<>(legacyLines.size());
        for (String s : legacyLines) {
            if (s == null) { out.add(style(Component.empty())); continue; }
            Component c = (s.indexOf('§') >= 0 ? sec : amp).deserialize(s);
            out.add(style(c));
        }
        m.lore(out);
        stack.setItemMeta(m);
        return this;
    }

    public ItemBuilder addFlag(ItemFlag... flags) {
        ItemMeta m = meta();
        m.addItemFlags(flags);
        stack.setItemMeta(m);
        return this;
    }

    public ItemBuilder hideAllFlags() {
        return addFlag(ItemFlag.values());
    }

    public ItemBuilder enchant(Enchantment e, int level) {
        if (e == null || level <= 0) return this;
        ItemMeta m = meta();
        m.addEnchant(e, level, true);
        stack.setItemMeta(m);
        return this;
    }

    public ItemBuilder glow(boolean enabled) {
        if (!enabled) return this;
        if (stack.getEnchantments().isEmpty()) {
            enchant(Enchantment.LURE, 1);
            addFlag(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder unbreakable(boolean u) {
        ItemMeta m = meta();
        m.setUnbreakable(u);
        stack.setItemMeta(m);
        return this;
    }

    public ItemBuilder damage(int dmg) {
        ItemMeta m = meta();
        if (m instanceof Damageable d) {
            d.setDamage(Math.max(0, dmg));
            stack.setItemMeta(m);
        }
        return this;
    }

    public ItemBuilder leatherColor(Color c) {
        ItemMeta m = meta();
        if (m instanceof LeatherArmorMeta lam && c != null) {
            lam.setColor(c);
            stack.setItemMeta(m);
        }
        return this;
    }

    public ItemBuilder texture(String base64) {
        if (stack.getType() != Material.PLAYER_HEAD || base64 == null || base64.isEmpty()) return this;
        SkullMeta m = (SkullMeta) meta();
        UUID id = UUID.nameUUIDFromBytes(Objects.requireNonNull(base64).getBytes());
        PlayerProfile profile = Bukkit.createProfile(id, null);
        profile.setProperty(new ProfileProperty("textures", base64));
        m.setPlayerProfile(profile);
        stack.setItemMeta(m);
        return this;
    }

    public ItemBuilder skullOwner(UUID owner) {
        if (stack.getType() != Material.PLAYER_HEAD || owner == null) return this;
        SkullMeta m = (SkullMeta) meta();
        PlayerProfile profile = Bukkit.createProfile(owner, null);
        m.setPlayerProfile(profile);
        stack.setItemMeta(m);
        return this;
    }
}
