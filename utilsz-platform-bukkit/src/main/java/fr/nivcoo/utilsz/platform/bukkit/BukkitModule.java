package fr.nivcoo.utilsz.platform.bukkit;

import fr.nivcoo.utilsz.core.config.annotations.Converter;
import fr.nivcoo.utilsz.core.module.UtilsZModule;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.ItemStackConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.LocationConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.MaterialConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.ParticleConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.SoundConv;
import fr.nivcoo.utilsz.platform.bukkit.messaging.BukkitMessagingAdapters;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class BukkitModule implements UtilsZModule {

    @Override
    public boolean isCompatible() {
        try {
            Class.forName("org.bukkit.Bukkit", false, BukkitModule.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public void onLoad() {
        BukkitMessagingAdapters.register();
    }

    @Override
    public Map<Class<?>, Supplier<Converter<?>>> converters() {
        Map<Class<?>, Supplier<Converter<?>>> converters = new LinkedHashMap<>();
        converters.put(Material.class, MaterialConv::new);
        converters.put(Sound.class, SoundConv::new);
        converters.put(Particle.class, ParticleConv::new);
        converters.put(Location.class, LocationConv::new);
        converters.put(ItemStack.class, ItemStackConv::new);
        return converters;
    }
}
