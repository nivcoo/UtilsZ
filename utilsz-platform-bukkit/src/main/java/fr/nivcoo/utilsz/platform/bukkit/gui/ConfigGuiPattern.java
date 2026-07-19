package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.annotations.Optional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class ConfigGuiPattern {

    @Optional
    public Integer rows = null;
    @Optional
    public Map<String, ConfigGuiItem> items = new LinkedHashMap<>();
    @Optional
    public Map<String, ConfigGuiItem> customItems = new LinkedHashMap<>();
    @Optional
    public Map<String, List<Integer>> regions = new LinkedHashMap<>();
}
