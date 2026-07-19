package fr.nivcoo.utilsz.platform.bukkit.gui;

import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.config.NamedConfig;
import fr.nivcoo.utilsz.platform.bukkit.item.ConfigItemFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class ConfigGuiRegistry {

    public static final String MENUS_DIRECTORY = "gui/menus";
    public static final String PATTERNS_DIRECTORY = "gui/patterns";

    private final ConfigManager configs;
    private final Logger logger;
    private final String inventoryKeyPrefix = "utilsz:configured-gui:" + UUID.randomUUID() + ':';
    private final Map<String, Supplier<? extends ConfigGuiMenu>> menuDefaults = new LinkedHashMap<>();
    private final Map<String, Supplier<? extends ConfigGuiPattern>> patternDefaults = new LinkedHashMap<>();
    private final Map<String, ConfiguredGui> handles = new ConcurrentHashMap<>();
    private final AtomicReference<State> state = new AtomicReference<>(new State(0L, Map.of()));

    public ConfigGuiRegistry(ConfigManager configs, Logger logger) {
        this.configs = Objects.requireNonNull(configs, "configs");
        this.logger = logger;
    }

    public synchronized ConfigGuiRegistry registerMenu(String id, Supplier<? extends ConfigGuiMenu> defaults) {
        register(menuDefaults, id, defaults, "menu");
        return this;
    }

    public synchronized ConfigGuiRegistry registerPattern(String id, Supplier<? extends ConfigGuiPattern> defaults) {
        register(patternDefaults, id, defaults, "pattern");
        return this;
    }

    public synchronized void reload() {
        Map<String, ConfigGuiPattern> patterns = loadPatterns();
        Map<String, ConfigGuiMenu> menus = loadMenus();
        long revision = state.get().revision() + 1L;

        validatePatterns(patterns);
        Map<String, ResolvedConfigGuiMenu> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigGuiMenu> entry : menus.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getKey(), entry.getValue(), patterns, revision));
        }
        state.set(new State(revision, immutableMap(resolved)));
    }

    public ConfiguredGui menu(String id) {
        String normalized = normalizePathId(id, "menu");
        return handles.computeIfAbsent(normalized, key -> new ConfiguredGui(this, key,
                inventoryKeyPrefix + key));
    }

    public Set<String> menuIds() {
        return state.get().menus().keySet();
    }

    public long revision() {
        return state.get().revision();
    }

    Logger logger() {
        return logger;
    }

    ResolvedConfigGuiMenu resolve(String id) {
        ResolvedConfigGuiMenu menu = state.get().menus().get(id);
        if (menu == null) throw new IllegalStateException("Unknown configured GUI menu: " + id);
        return menu;
    }

    private Map<String, ConfigGuiPattern> loadPatterns() {
        return index(configs.loadAllNamed(
                PATTERNS_DIRECTORY, ConfigGuiPattern.class, true, patternDefaults));
    }

    private Map<String, ConfigGuiMenu> loadMenus() {
        return index(configs.loadAllNamed(
                MENUS_DIRECTORY, ConfigGuiMenu.class, true, menuDefaults));
    }

    private static <T> Map<String, T> index(List<NamedConfig<T>> discovered) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (NamedConfig<T> named : discovered) {
            String id = normalizePathId(named.id(), "config");
            if (indexed.putIfAbsent(id, named.value()) != null) {
                throw new IllegalArgumentException("Duplicate configured GUI id '" + id + "'");
            }
        }
        return indexed;
    }

    private static ResolvedConfigGuiMenu resolve(
            String menuId,
            ConfigGuiMenu menu,
            Map<String, ConfigGuiPattern> patterns,
            long revision
    ) {
        if (menu == null) throw new IllegalArgumentException("Menu '" + menuId + "' is null");
        int rows = validateRows(menu.rows, "menu '" + menuId + "'");
        Map<String, ConfigGuiItem> items = new LinkedHashMap<>();
        Map<String, ConfigGuiItem> customItems = new LinkedHashMap<>();
        Map<String, List<Integer>> regions = new LinkedHashMap<>();

        for (String reference : safeList(menu.patterns)) {
            String patternId = normalizePathId(reference, "pattern reference");
            ConfigGuiPattern pattern = patterns.get(patternId);
            if (pattern == null) {
                throw new IllegalArgumentException("Menu '" + menuId + "' references missing pattern '"
                        + patternId + "'");
            }
            if (pattern.rows != null && validateRows(pattern.rows, "pattern '" + patternId + "'") != rows) {
                throw new IllegalArgumentException("Pattern '" + patternId + "' expects " + pattern.rows
                        + " rows but menu '" + menuId + "' uses " + rows);
            }
            mergeItems(items, pattern.items, "pattern '" + patternId + "' items");
            mergeItems(customItems, pattern.customItems, "pattern '" + patternId + "' custom_items");
            mergeRegions(regions, pattern.regions, "pattern '" + patternId + "' regions");
        }

        mergeItems(items, menu.items, "menu '" + menuId + "' items");
        mergeItems(customItems, menu.customItems, "menu '" + menuId + "' custom_items");
        mergeRegions(regions, menu.regions, "menu '" + menuId + "' regions");
        validateSlots(items, rows, "menu '" + menuId + "' items", false);
        validateSlots(customItems, rows, "menu '" + menuId + "' custom_items", true);
        validateRegions(regions, rows, "menu '" + menuId + "' regions");

        return new ResolvedConfigGuiMenu(
                menuId,
                revision,
                menu.title == null ? Component.empty() : menu.title,
                rows,
                immutableMap(items),
                immutableMap(customItems),
                immutableMap(regions)
        );
    }

    private static void mergeItems(
            Map<String, ConfigGuiItem> target,
            Map<String, ConfigGuiItem> source,
            String location
    ) {
        if (source == null) return;
        Set<String> localIds = new HashSet<>();
        for (Map.Entry<String, ConfigGuiItem> entry : source.entrySet()) {
            String id = normalizeItemId(entry.getKey(), location);
            if (!localIds.add(id)) throw new IllegalArgumentException("Duplicate item id '" + id + "' in " + location);
            ConfigGuiItem item = entry.getValue();
            if (item == null) throw new IllegalArgumentException("Item '" + id + "' is null in " + location);
            ConfigGuiItem copy = (ConfigGuiItem) ConfigItemFactory.copy(item);
            if (copy.slots != null && !copy.slots.isEmpty()) copy.slot = null;
            target.put(id, copy);
        }
    }

    private static void mergeRegions(
            Map<String, List<Integer>> target,
            Map<String, List<Integer>> source,
            String location
    ) {
        if (source == null) return;
        Set<String> localIds = new HashSet<>();
        for (Map.Entry<String, List<Integer>> entry : source.entrySet()) {
            String id = normalizeItemId(entry.getKey(), location);
            if (!localIds.add(id)) throw new IllegalArgumentException("Duplicate region id '" + id + "' in " + location);
            target.put(id, entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
        }
    }

    private static void validatePatterns(Map<String, ConfigGuiPattern> patterns) {
        for (Map.Entry<String, ConfigGuiPattern> entry : patterns.entrySet()) {
            String patternId = entry.getKey();
            ConfigGuiPattern pattern = entry.getValue();
            if (pattern == null) throw new IllegalArgumentException("Pattern '" + patternId + "' is null");
            int rows = pattern.rows == null ? 6 : validateRows(pattern.rows, "pattern '" + patternId + "'");
            Map<String, ConfigGuiItem> items = new LinkedHashMap<>();
            Map<String, ConfigGuiItem> customItems = new LinkedHashMap<>();
            Map<String, List<Integer>> regions = new LinkedHashMap<>();
            mergeItems(items, pattern.items, "pattern '" + patternId + "' items");
            mergeItems(customItems, pattern.customItems, "pattern '" + patternId + "' custom_items");
            mergeRegions(regions, pattern.regions, "pattern '" + patternId + "' regions");
            validateSlots(items, rows, "pattern '" + patternId + "' items", false);
            validateSlots(customItems, rows, "pattern '" + patternId + "' custom_items", true);
            validateRegions(regions, rows, "pattern '" + patternId + "' regions");
        }
    }

    private static void validateSlots(
            Map<String, ConfigGuiItem> items,
            int rows,
            String location,
            boolean custom
    ) {
        for (Map.Entry<String, ConfigGuiItem> entry : items.entrySet()) {
            ConfigGuiItem item = entry.getValue();
            List<Integer> slots = slots(item);
            if (custom && item.enabled && slots.isEmpty()) {
                throw new IllegalArgumentException("Enabled custom item '" + entry.getKey()
                        + "' has no slot in " + location);
            }
            if (custom && item.enabled && isAir(item.material)) {
                throw new IllegalArgumentException("Enabled custom item '" + entry.getKey()
                        + "' has no visible material in " + location);
            }
            validateSlotList(slots, rows, location + "." + entry.getKey());
        }
    }

    private static void validateRegions(Map<String, List<Integer>> regions, int rows, String location) {
        for (Map.Entry<String, List<Integer>> entry : regions.entrySet()) {
            validateSlotList(entry.getValue(), rows, location + "." + entry.getKey());
        }
    }

    private static void validateSlotList(List<Integer> slots, int rows, String location) {
        Set<Integer> unique = new HashSet<>();
        int size = rows * 9;
        for (Integer slot : slots) {
            if (slot == null) throw new IllegalArgumentException("Null slot in " + location);
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("Slot " + slot + " in " + location
                        + " is outside 0-" + (size - 1));
            }
            if (!unique.add(slot)) throw new IllegalArgumentException("Duplicate slot " + slot + " in " + location);
        }
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR
                || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static List<Integer> slots(ConfigGuiItem item) {
        if (item == null) return List.of();
        if (item.slots != null && !item.slots.isEmpty()) return List.copyOf(item.slots);
        return item.slot == null ? List.of() : List.of(item.slot);
    }

    private static int validateRows(int rows, String location) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Invalid row count " + rows + " for " + location + " (expected 1-6)");
        }
        return rows;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> void register(
            Map<String, Supplier<? extends T>> target,
            String id,
            Supplier<? extends T> defaults,
            String kind
    ) {
        String normalized = normalizePathId(id, kind);
        Objects.requireNonNull(defaults, "defaults");
        if (target.putIfAbsent(normalized, defaults) != null) {
            throw new IllegalArgumentException("Duplicate configured GUI " + kind + ": " + normalized);
        }
    }

    private static String normalizePathId(String value, String kind) {
        if (value == null) throw new IllegalArgumentException(kind + " id cannot be null");
        String normalized = value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.endsWith("/")
                || normalized.contains("//") || normalized.contains("../") || normalized.contains("/..")
                || normalized.equals(".") || normalized.equals("..")
                || !normalized.matches("[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)*")) {
            throw new IllegalArgumentException("Invalid " + kind + " id: " + value);
        }
        return normalized;
    }

    private static String normalizeItemId(String value, String location) {
        if (value == null) throw new IllegalArgumentException("Null id in " + location);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("Invalid id '" + value + "' in " + location);
        }
        return normalized;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private record State(long revision, Map<String, ResolvedConfigGuiMenu> menus) {
    }
}
