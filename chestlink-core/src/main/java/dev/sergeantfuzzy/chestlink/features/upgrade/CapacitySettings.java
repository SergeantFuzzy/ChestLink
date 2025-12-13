package dev.sergeantfuzzy.chestlink.features.upgrade;

import dev.sergeantfuzzy.chestlink.core.data.InventoryType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Configuration container for capacity upgrade sizing rules.
 */
public class CapacitySettings {
    private final TreeMap<Integer, Integer> defaultLevelSlots;
    private final TreeMap<Integer, Integer> singleLevelSlots;
    private final TreeMap<Integer, Integer> doubleLevelSlots;
    private final int maxInventorySize;

    public CapacitySettings(Map<Integer, Integer> levelSlots, Map<Integer, Integer> singleLevelSlots, Map<Integer, Integer> doubleLevelSlots, int maxInventorySize) {
        this.defaultLevelSlots = new TreeMap<>(levelSlots != null ? levelSlots : Collections.emptyMap());
        this.singleLevelSlots = new TreeMap<>(singleLevelSlots != null ? singleLevelSlots : Collections.emptyMap());
        this.doubleLevelSlots = new TreeMap<>(doubleLevelSlots != null ? doubleLevelSlots : Collections.emptyMap());
        this.maxInventorySize = maxInventorySize <= 0 ? Integer.MAX_VALUE : maxInventorySize;
    }

    public static CapacitySettings empty() {
        return new CapacitySettings(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Integer.MAX_VALUE);
    }

    public int getMaxInventorySize() {
        return maxInventorySize;
    }

    public Map<Integer, Integer> getLevelSlots() {
        return Collections.unmodifiableMap(defaultLevelSlots);
    }

    /**
     * Returns the configured size (in slots) for the given level, ensuring it is at least the base size.
     */
    public int sizeForLevel(int level, InventoryType type, int currentCapacity) {
        int baseSize = type != null ? type.getSize() : currentCapacity;
        if (level <= 0) {
            return Math.max(baseSize, currentCapacity);
        }
        NavigableMap<Integer, Integer> map = slotsFor(type);
        Map.Entry<Integer, Integer> entry = map.floorEntry(level);
        if (entry == null) {
            return Math.max(baseSize, currentCapacity);
        }
        int configured = Math.max(1, entry.getValue());
        int clamped = maxInventorySize > 0 ? Math.min(configured, maxInventorySize) : configured;
        return Math.max(Math.max(baseSize, clamped), currentCapacity);
    }

    public static CapacitySettings fromSection(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return CapacitySettings.empty();
        }
        TreeMap<Integer, Integer> slots = new TreeMap<>();
        TreeMap<Integer, Integer> single = new TreeMap<>();
        TreeMap<Integer, Integer> doubles = new TreeMap<>();
        readLevelSection(section.getConfigurationSection("levels"), slots, logger);
        readLevelSection(section.getConfigurationSection("single-levels"), single, logger);
        readLevelSection(section.getConfigurationSection("double-levels"), doubles, logger);
        int rawMax = section.getInt("max-slots", -1);
        int maxSize = rawMax <= 0 ? Integer.MAX_VALUE : rawMax;
        return new CapacitySettings(slots, single, doubles, maxSize);
    }

    private static Integer parseLevel(String key, Logger logger) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ex) {
            logger.warning("[ChestLink] Invalid capacity level key '" + key + "'. Expected a number.");
            return null;
        }
    }

    private static int readSlots(ConfigurationSection section, String key) {
        if (section.isInt(key)) {
            return section.getInt(key);
        }
        ConfigurationSection nested = section.getConfigurationSection(key);
        if (nested != null) {
            if (nested.isInt("slots")) {
                return nested.getInt("slots");
            }
            String raw = nested.getString("slots", "0");
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        String fallback = section.getString(key, "0");
        try {
            return Integer.parseInt(fallback);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void readLevelSection(ConfigurationSection levelSection, Map<Integer, Integer> target, Logger logger) {
        if (levelSection == null) {
            return;
        }
        for (String key : levelSection.getKeys(false)) {
            Integer level = parseLevel(key, logger);
            if (level == null) {
                continue;
            }
            int value = readSlots(levelSection, key);
            if (value <= 0) {
                logger.warning("[ChestLink] Capacity level '" + key + "' has invalid slot count '" + value + "'.");
                continue;
            }
            target.put(level, value);
        }
    }

    private NavigableMap<Integer, Integer> slotsFor(InventoryType type) {
        if (type == InventoryType.SINGLE && !singleLevelSlots.isEmpty()) {
            return singleLevelSlots;
        }
        if (type == InventoryType.DOUBLE && !doubleLevelSlots.isEmpty()) {
            return doubleLevelSlots;
        }
        return defaultLevelSlots;
    }
}