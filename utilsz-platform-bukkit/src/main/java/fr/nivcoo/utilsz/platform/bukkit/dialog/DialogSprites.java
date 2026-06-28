package fr.nivcoo.utilsz.platform.bukkit.dialog;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;

@SuppressWarnings("unused")
public final class DialogSprites {

    private DialogSprites() {
    }

    public static Component sprite(String key) {
        return Component.object(ObjectContents.sprite(Key.key(key)));
    }

    public static Component sprite(Key key) {
        return Component.object(ObjectContents.sprite(key));
    }

    public static Component material(Material material) {
        Material value = material == null || material.isAir() ? Material.BARRIER : material;
        String prefix = value.isBlock() ? "block/" : "item/";
        return sprite("minecraft:" + prefix + value.key().value());
    }
}
