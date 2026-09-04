package fr.nivcoo.utilsz.platform.bukkit.tracking;

public interface BlockChangeProvider {

    String id();

    void apply(BlockChangeService.Changes changes);
}
