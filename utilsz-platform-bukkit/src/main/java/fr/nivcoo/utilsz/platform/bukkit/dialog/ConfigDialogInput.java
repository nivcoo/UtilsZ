package fr.nivcoo.utilsz.platform.bukkit.dialog;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ConfigDialogInput {

    public ConfigDialogInputType type = ConfigDialogInputType.TEXT;
    public String key = "value";
    public Component label = ConfigManager.parseDynamic("&fValeur");
    public int width = 300;
    public boolean labelVisible = true;
    public String initialText = "";
    public int maxLength = 128;
    public Integer maxLines = null;
    public Integer multilineHeight = null;
    public boolean initialBoolean = false;
    public String onTrue = "true";
    public String onFalse = "false";
    public float start = 0f;
    public float end = 100f;
    public Float initialNumber = null;
    public Float step = null;
    public String labelFormat = "%s: %s";
    public List<ConfigDialogOption> options = new ArrayList<>();
}
