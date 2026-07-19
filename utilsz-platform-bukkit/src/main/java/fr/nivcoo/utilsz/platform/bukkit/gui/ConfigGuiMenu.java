package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.annotations.Optional;
import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class ConfigGuiMenu {

    public Component title = Component.empty();
    public int rows = 6;
    @Optional
    public List<String> patterns = List.of();
    @Optional
    public Map<String, ConfigGuiItem> items = new LinkedHashMap<>();
    @Optional
    public Map<String, ConfigGuiItem> customItems = new LinkedHashMap<>();
    @Optional
    public Map<String, List<Integer>> regions = new LinkedHashMap<>();
}
