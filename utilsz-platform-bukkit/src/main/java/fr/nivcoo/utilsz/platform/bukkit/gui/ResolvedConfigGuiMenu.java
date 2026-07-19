package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;

record ResolvedConfigGuiMenu(
        String id,
        long revision,
        Component title,
        int rows,
        Map<String, ConfigGuiItem> items,
        Map<String, ConfigGuiItem> customItems,
        Map<String, List<Integer>> regions
) {
}
