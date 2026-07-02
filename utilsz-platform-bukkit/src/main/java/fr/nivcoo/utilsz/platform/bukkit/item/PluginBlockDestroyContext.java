package fr.nivcoo.utilsz.platform.bukkit.item;

import org.bukkit.block.Block;
import org.bukkit.event.Event;

@SuppressWarnings("unused")
public record PluginBlockDestroyContext(Block block, PluginBlockDestroyCause cause, Event event) {
}
