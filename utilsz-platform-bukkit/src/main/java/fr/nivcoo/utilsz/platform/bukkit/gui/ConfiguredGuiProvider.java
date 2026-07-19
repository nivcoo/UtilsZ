package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;

import java.util.Map;

@SuppressWarnings("unused")
public interface ConfiguredGuiProvider extends GuiProvider {

    ConfiguredGui configuredGui();

    default Map<String, ?> guiPlaceholders(GuiInventory inventory) {
        return Map.of();
    }

    default ConfigGuiView configuredView(GuiInventory inventory) {
        return configuredGui().view(inventory, guiPlaceholders(inventory));
    }

    @Override
    default Component title(GuiInventory inventory) {
        return configuredGui().title(inventory, guiPlaceholders(inventory));
    }

    @Override
    default int rows(GuiInventory inventory) {
        return configuredGui().rows(inventory);
    }
}
