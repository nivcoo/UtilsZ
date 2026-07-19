package fr.nivcoo.utilsz.platform.bukkit.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class ConfigGuiMenuRegistration {

    private final String menuId;
    private final Map<String, RegionConstraint> regionConstraints = new LinkedHashMap<>();
    private final Map<String, RegionSizeMatch> regionSizeMatches = new LinkedHashMap<>();

    ConfigGuiMenuRegistration(String menuId) {
        this.menuId = menuId;
    }

    public ConfigGuiMenuRegistration requireRegion(String regionId) {
        return requireRegionSize(regionId, 1, Integer.MAX_VALUE);
    }

    public ConfigGuiMenuRegistration requireRegionSize(String regionId, int size) {
        return requireRegionSize(regionId, size, size);
    }

    public ConfigGuiMenuRegistration requireRegionSize(String regionId, int minimumSize, int maximumSize) {
        String region = regionId(regionId);
        if (minimumSize < 0 || maximumSize < minimumSize) {
            throw new IllegalArgumentException("Invalid size range " + minimumSize + "-" + maximumSize
                    + " for region '" + region + "' in menu '" + menuId + "'");
        }
        RegionConstraint constraint = new RegionConstraint(region, minimumSize, maximumSize);
        if (regionConstraints.putIfAbsent(region, constraint) != null) {
            throw new IllegalArgumentException("Duplicate region constraint '" + region + "' in menu '" + menuId + "'");
        }
        return this;
    }

    public ConfigGuiMenuRegistration requireSameRegionSize(String firstRegionId, String secondRegionId) {
        String first = regionId(firstRegionId);
        String second = regionId(secondRegionId);
        if (first.equals(second)) {
            throw new IllegalArgumentException("Region size match requires two different regions in menu '"
                    + menuId + "'");
        }
        String lower = first.compareTo(second) < 0 ? first : second;
        String upper = first.compareTo(second) < 0 ? second : first;
        String key = lower + '\0' + upper;
        RegionSizeMatch match = new RegionSizeMatch(first, second);
        if (regionSizeMatches.putIfAbsent(key, match) != null) {
            throw new IllegalArgumentException("Duplicate region size match in menu '" + menuId + "': "
                    + first + ", " + second);
        }
        return this;
    }

    void validate(ResolvedConfigGuiMenu menu) {
        for (RegionConstraint constraint : regionConstraints.values()) {
            List<Integer> slots = region(menu, constraint.regionId());
            int size = slots.size();
            if (size < constraint.minimumSize() || size > constraint.maximumSize()) {
                String expected = constraint.minimumSize() == constraint.maximumSize()
                        ? String.valueOf(constraint.minimumSize())
                        : constraint.minimumSize() + "-" + constraint.maximumSize();
                throw new IllegalArgumentException("Region '" + constraint.regionId() + "' in menu '"
                        + menuId + "' has " + size + " slots, expected " + expected);
            }
        }
        for (RegionSizeMatch match : regionSizeMatches.values()) {
            List<Integer> first = region(menu, match.firstRegionId());
            List<Integer> second = region(menu, match.secondRegionId());
            if (first.size() != second.size()) {
                throw new IllegalArgumentException("Regions '" + match.firstRegionId() + "' and '"
                        + match.secondRegionId() + "' in menu '" + menuId
                        + "' must have the same number of slots");
            }
        }
    }

    private String regionId(String value) {
        return ConfigGuiRegistry.normalizeItemId(value, "menu '" + menuId + "' region constraint");
    }

    private List<Integer> region(ResolvedConfigGuiMenu menu, String regionId) {
        List<Integer> slots = menu.regions().get(regionId);
        if (slots == null) {
            throw new IllegalArgumentException("Missing required region '" + regionId
                    + "' in menu '" + menuId + "'");
        }
        return slots;
    }

    private record RegionConstraint(String regionId, int minimumSize, int maximumSize) {
    }

    private record RegionSizeMatch(String firstRegionId, String secondRegionId) {
    }
}
