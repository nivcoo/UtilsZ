package fr.nivcoo.utilsz.platform.bukkit;

import fr.nivcoo.utilsz.core.conversion.Converter;
import fr.nivcoo.utilsz.core.module.UtilsZModule;
import fr.nivcoo.utilsz.platform.bukkit.conversion.ItemStackConv;
import fr.nivcoo.utilsz.platform.bukkit.conversion.MaterialConv;
import fr.nivcoo.utilsz.platform.bukkit.conversion.ParticleConv;
import fr.nivcoo.utilsz.platform.bukkit.conversion.PotionEffectTypeConv;
import fr.nivcoo.utilsz.platform.bukkit.conversion.SoundConv;
import fr.nivcoo.utilsz.platform.bukkit.conversion.StoredLocationConv;
import fr.nivcoo.utilsz.platform.bukkit.conversion.VillagerProfessionConv;
import fr.nivcoo.utilsz.platform.bukkit.conversion.VillagerTypeConv;
import fr.nivcoo.utilsz.platform.bukkit.location.StoredLocation;
import fr.nivcoo.utilsz.platform.bukkit.messaging.BukkitMessagingAdapters;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

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
        converters.put(PotionEffectType.class, PotionEffectTypeConv::new);
        converters.put(Villager.Profession.class, VillagerProfessionConv::new);
        converters.put(Villager.Type.class, VillagerTypeConv::new);
        converters.put(StoredLocation.class, StoredLocationConv::new);
        converters.put(ItemStack.class, ItemStackConv::new);
        return converters;
    }

}
