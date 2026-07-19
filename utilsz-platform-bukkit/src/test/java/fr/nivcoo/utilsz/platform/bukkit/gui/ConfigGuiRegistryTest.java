package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItemFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigGuiRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void patternsMergeInOrderAndMenuWinsByIdentifier() {
        ConfigGuiRegistry registry = registry()
                .registerPattern("first", () -> pattern(Material.STONE, 0))
                .registerPattern("second", () -> pattern(Material.DIRT, 1))
                .registerMenu("example", () -> menu(Material.DIAMOND, List.of("first", "second")));

        registry.reload();
        ResolvedConfigGuiMenu resolved = registry.resolve("example");

        assertEquals(1L, resolved.revision());
        assertEquals(Material.DIAMOND, resolved.items().get("shared").material);
        assertEquals(List.of(2), resolved.regions().get("content"));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, resolved.customItems().get("frame").material);
        assertEquals(List.of("shared"), resolved.items().keySet().stream().toList());
    }

    @Test
    void failedReloadKeepsThePreviousResolvedSnapshot() throws Exception {
        ConfigGuiRegistry registry = registry()
                .registerPattern("frame", () -> pattern(Material.STONE, 0))
                .registerMenu("example", () -> menu(Material.DIAMOND, List.of("frame")));
        registry.reload();
        ResolvedConfigGuiMenu before = registry.resolve("example");

        Files.writeString(tempDir.resolve("gui/patterns/frame.yml"), "rows: 3\n", StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, registry::reload);
        assertEquals(1L, registry.revision());
        assertSame(before, registry.resolve("example"));
    }

    @Test
    void recursivelyDiscoveredPatternsCanBeReferenced() throws Exception {
        Path pattern = tempDir.resolve("gui/patterns/admin/pagination.yml");
        Files.createDirectories(pattern.getParent());
        Files.writeString(pattern, """
                rows: 6
                items:
                  previous:
                    material: PAPER
                    slot: 47
                """, StandardCharsets.UTF_8);

        ConfigGuiRegistry registry = registry()
                .registerMenu("admin/logs", () -> menu(Material.DIAMOND, List.of("admin/pagination")));

        registry.reload();

        assertEquals(Material.PAPER, registry.resolve("admin/logs").items().get("previous").material);
        assertEquals(47, registry.resolve("admin/logs").items().get("previous").slot);
        assertTrue(registry.menuIds().contains("admin/logs"));
    }

    @Test
    void configuredMultipleSlotsOverrideTheDefaultSingleSlot() throws Exception {
        Path menuFile = tempDir.resolve("gui/menus/example.yml");
        Files.createDirectories(menuFile.getParent());
        Files.writeString(menuFile, """
                items:
                  shared:
                    slots: [45, 46]
                """, StandardCharsets.UTF_8);
        ConfigGuiRegistry registry = registry()
                .registerMenu("example", () -> menu(Material.DIAMOND, List.of()));

        registry.reload();
        ConfigGuiItem item = registry.resolve("example").items().get("shared");
        String saved = Files.readString(menuFile, StandardCharsets.UTF_8);

        assertNull(item.slot);
        assertEquals(List.of(45, 46), item.slots);
        assertFalse(saved.contains("slot:"));
        assertTrue(saved.contains("slots:"));
    }

    @Test
    void explicitlyConfiguredSlotAndSlotsAreNormalizedOnFirstSave() throws Exception {
        Path menuFile = tempDir.resolve("gui/menus/example.yml");
        Files.createDirectories(menuFile.getParent());
        Files.writeString(menuFile, """
                items:
                  shared:
                    slot: 2
                    slots: [45, 46]
                """, StandardCharsets.UTF_8);
        ConfigGuiRegistry registry = registry()
                .registerMenu("example", () -> menu(Material.DIAMOND, List.of()));

        registry.reload();
        String saved = Files.readString(menuFile, StandardCharsets.UTF_8);

        assertNull(registry.resolve("example").items().get("shared").slot);
        assertFalse(saved.contains("slot:"));
        assertTrue(saved.contains("slots:"));
    }

    @Test
    void customItemsDoNotRenderInExcludedSlots() {
        ConfigGuiItem fill = new ConfigGuiItem();
        fill.slots = List.of(0, 1, 2);

        ConfigGuiItem visible = ConfigGuiView.withoutExcludedSlots(fill, java.util.Set.of(1));

        assertEquals(List.of(0, 2), visible.slots);
        assertEquals(List.of(0, 1, 2), fill.slots);
        assertNull(ConfigGuiView.withoutExcludedSlots(item(Material.STONE, 1), java.util.Set.of(1)));
    }

    @Test
    void copiedItemsOwnTheirEnchantMap() {
        ConfigGuiItem source = item(Material.STONE, 1);
        source.enchants = new java.util.LinkedHashMap<>(Map.of("minecraft:unbreaking", 1));

        ConfigGuiItem copy = (ConfigGuiItem) ConfigItemFactory.copy(source);
        copy.enchants.put("minecraft:fortune", 3);

        assertEquals(Map.of("minecraft:unbreaking", 1), source.enchants);
    }

    @Test
    void rejectsOutOfBoundsAndIncompatiblePatterns() {
        ConfigGuiRegistry invalidSlot = registry()
                .registerMenu("invalid-slot", () -> {
                    ConfigGuiMenu menu = menu(Material.STONE, List.of());
                    menu.rows = 3;
                    menu.items.put("outside", item(Material.STONE, 27));
                    return menu;
                });
        assertThrows(IllegalArgumentException.class, invalidSlot::reload);

        ConfigGuiRegistry incompatibleRows = registry()
                .registerPattern("three", () -> {
                    ConfigGuiPattern pattern = pattern(Material.STONE, 0);
                    pattern.rows = 3;
                    return pattern;
                })
                .registerMenu("six", () -> menu(Material.STONE, List.of("three")));
        assertThrows(IllegalArgumentException.class, incompatibleRows::reload);
    }

    private ConfigGuiRegistry registry() {
        return new ConfigGuiRegistry(new ConfigManager(tempDir.toFile()), null);
    }

    private static ConfigGuiPattern pattern(Material sharedMaterial, int sharedSlot) {
        ConfigGuiPattern pattern = new ConfigGuiPattern();
        pattern.rows = 6;
        pattern.items.put("shared", item(sharedMaterial, sharedSlot));
        pattern.customItems.put("frame", item(Material.GRAY_STAINED_GLASS_PANE, 0));
        pattern.regions.put("content", List.of(sharedSlot));
        return pattern;
    }

    private static ConfigGuiMenu menu(Material sharedMaterial, List<String> patterns) {
        ConfigGuiMenu menu = new ConfigGuiMenu();
        menu.title = Component.text("Example");
        menu.rows = 6;
        menu.patterns = patterns;
        menu.items.put("shared", item(sharedMaterial, 2));
        menu.regions.put("content", List.of(2));
        return menu;
    }

    private static ConfigGuiItem item(Material material, int slot) {
        ConfigGuiItem item = new ConfigGuiItem();
        item.material = material;
        item.slot = slot;
        return item;
    }
}
