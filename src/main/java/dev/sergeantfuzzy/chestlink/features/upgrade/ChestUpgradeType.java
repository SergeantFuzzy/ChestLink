package dev.sergeantfuzzy.chestlink.features.upgrade;

import java.util.Locale;

/**
 * Supported per-chest upgrade types. This is intentionally data-only for now so future
 * systems (commands, GUI, config) can reference a stable set of keys.
 */
public enum ChestUpgradeType {
    CAPACITY("capacity", "Capacity", 3, "Expand virtual storage beyond the base inventory."),
    AUTO_SORT("auto_sort", "Auto-Sort", 1, "Automatically sorts contents after items are added."),
    FILTER("filter", "Auto-Filter", 1, "Filters incoming items by whitelist/blacklist rules."),
    COMPRESSION("compression", "Compression", 1, "Compress items into higher tiers when possible.");

    private final String key;
    private final String displayName;
    private final int maxLevel;
    private final String description;

    ChestUpgradeType(String key, String displayName, int maxLevel, String description) {
        this.key = key;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getDescription() {
        return description;
    }

    public static ChestUpgradeType fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ENGLISH).replace('-', '_');
        if ("sorting".equals(normalized)) {
            normalized = "auto_sort";
        }
        for (ChestUpgradeType type : values()) {
            if (type.key.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}