package fr.nivcoo.utilsz.platform.bukkit.config;

import fr.nivcoo.utilsz.core.config.spi.ConverterProvider;
import fr.nivcoo.utilsz.core.config.annotations.Converter;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.ItemStackConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.LocationConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.MaterialConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.ParticleConv;
import fr.nivcoo.utilsz.platform.bukkit.config.conv.SoundConv;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class BukkitConverterProvider implements ConverterProvider {

    @Override
    public Map<Class<?>, Supplier<Converter<?>>> converters() {
        Map<Class<?>, Supplier<Converter<?>>> m = new LinkedHashMap<>();

        m.put(Material.class, MaterialConv::new);
        m.put(Sound.class, SoundConv::new);
        m.put(Particle.class, ParticleConv::new);
        m.put(Location.class, LocationConv::new);
        m.put(ItemStack.class, ItemStackConv::new);

        return m;
    }

    @Override
    public int priority() {
        return 100;
    }
}
