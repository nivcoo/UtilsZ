/**
 *
 */
package fr.nivcoo.utilsz.inventory;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public class ItemBuilder {
    private Material m;
    private int count;
    private short data;
    private String name;
    private List<String> lores;
    private String texture;

    private ItemBuilder() {
    }

    public static ItemBuilder of(Material m) {
        return of(m, 1);
    }

    public static ItemBuilder of(Material m, int count) {
        return of(m, count, (short) 0);
    }

    public static ItemBuilder of(Material m, int count, short data) {
        ItemBuilder ib = new ItemBuilder();
        ib.m = m;
        ib.count = count;
        ib.data = data;
        return ib;
    }

    public ItemBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ItemBuilder lore(List<String> lores) {
        this.lores = lores;
        return this;
    }

    public ItemBuilder texture(String texture) {
        this.texture = texture;
        return this;
    }

    public ItemStack build() {
        ItemStack is = new ItemStack(m, count, data);
        ItemMeta im = is.hasItemMeta() ? is.getItemMeta() : Bukkit.getItemFactory().getItemMeta(m);
        if (im != null && (name != null || lores != null)) {
            if (name != null)
                im.displayName(Component.text(name));
            if (lores != null)
                im.lore(lores.stream().map(Component::text).collect(Collectors.toList()));
            is.setItemMeta(im);
        }

        return is;
    }
}
