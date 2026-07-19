package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class ConfigGuiMenu {

    public Component title = Component.empty();
    public int rows = 6;
    public List<String> patterns = List.of();
    public Map<String, ConfigGuiItem> items = new LinkedHashMap<>();
    public Map<String, ConfigGuiItem> customItems = new LinkedHashMap<>();
    public Map<String, List<Integer>> regions = new LinkedHashMap<>();
}
