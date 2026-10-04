package fr.nivcoo.utilsz.platform.bukkit.reward.type;

import fr.nivcoo.utilsz.platform.bukkit.reward.RewardAction;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardCompileContext;
import fr.nivcoo.utilsz.platform.bukkit.reward.RewardStep;

import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItem;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItemFactory;
import fr.nivcoo.utilsz.platform.bukkit.item.ItemDelivery;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class ItemRewardType implements RewardType {
    public static final String ID = "ITEM";

    @Override
    public String id() { return ID; }

    @Override
    public RewardAction compile(RewardCompileContext context) {
        context.requireOptions(Set.of("items"));
        if (!(context.options().get("items") instanceof List<?> sources) || sources.isEmpty())
            throw context.invalid("options.items must be a non-empty list");
        List<CompiledItem> items = new ArrayList<>(sources.size());
        List<Supplier<ItemStack>> displays = new ArrayList<>(sources.size());
        for (Object source : sources) {
            CompiledItem item = new CompiledItem(context.decodeItem(source), context.logger());
            items.add(item);
            displays.add(item::display);
        }
        return new RewardAction(ID, displays, execution -> {
            var player = execution.requirePlayer();
            List<RewardStep> steps = new ArrayList<>(items.size());
            for (CompiledItem source : items) {
                ItemStack item = source.item();
                steps.add(new RewardStep(RewardAction.CheckResult::ready, () -> {
                    ItemDelivery.giveOrDrop(player, item, source.amount);
                    return RewardAction.DeliveryResult.success();
                }));
            }
            return steps;
        });
    }

    private static final class CompiledItem {
        private final ConfigItem definition;
        private final int amount;
        private final Logger logger;
        private ItemStack template;

        private CompiledItem(ConfigItem definition, Logger logger) {
            this.definition = definition;
            this.amount = definition.amount;
            this.definition.amount = 1;
            this.logger = logger;
        }

        private ItemStack item() {
            if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Reward items require the server thread.");
            if (template == null) {
                if (!definition.material.isItem()) throw new IllegalArgumentException("Reward material must be an item.");
                ItemStack item = ConfigItemFactory.create(definition, logger);
                if (item == null || item.getType().isAir()) throw new IllegalArgumentException("Reward item could not be created.");
                template = item.clone();
            }
            return template.clone();
        }

        private ItemStack display() {
            ItemStack item = item();
            item.setAmount(Math.min(amount, Math.max(1, item.getMaxStackSize())));
            return item;
        }
    }
}
