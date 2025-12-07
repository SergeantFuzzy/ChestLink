package dev.sergeantfuzzy.chestlink.upgrade;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Configuration container for capacity upgrade sizing rules.
 */
public class CapacitySettings {
    private final TreeMap<Integer, Integer> levelSlots;
    private final int maxInventorySize;

    public CapacitySettings(Map<Integer, Integer> levelSlots, int maxInventorySize) {
        this.levelSlots = new TreeMap<>(levelSlots != null ? levelSlots : Collections.emptyMap());
        this.maxInventorySize = Math.max(9, normalizeSize(maxInventorySize));
    }

    public static CapacitySettings empty() {
        return new CapacitySettings(Collections.emptyMap(), 54);
    }

    public int getMaxInventorySize() {
        return maxInventorySize;
    }

    public Map<Integer, Integer> getLevelSlots() {
        return Collections.unmodifiableMap(levelSlots);
    }

    /**
     * Returns the configured size (in slots) for the given level, ensuring it is at least the base size.
     */
    public int sizeForLevel(int level, int baseSize) {
        if (level <= 0) {
            return baseSize;
        }
        Map.Entry<Integer, Integer> entry = levelSlots.floorEntry(level);
        if (entry == null) {
            return baseSize;
        }
        int configured = normalizeSize(entry.getValue());
        configured = Math.min(configured, maxInventorySize);
        return Math.max(baseSize, configured);
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return 9;
        }
        int normalized = Math.max(9, size - (size % 9));
        if (normalized == 0) {
            normalized = 9;
        }
        if (normalized > 81) {
            normalized = 81;
        }
        return normalized;
    }

    public static CapacitySettings fromSection(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return CapacitySettings.empty();
        }
        TreeMap<Integer, Integer> slots = new TreeMap<>();
        ConfigurationSection levelSection = section.getConfigurationSection("levels");
        if (levelSection != null) {
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
                slots.put(level, normalizeSize(value));
            }
        }
        int maxSize = normalizeSize(section.getInt("max-slots", 54));
        return new CapacitySettings(slots, maxSize);
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
}
