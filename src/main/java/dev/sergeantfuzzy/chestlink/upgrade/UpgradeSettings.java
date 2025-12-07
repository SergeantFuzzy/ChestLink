package dev.sergeantfuzzy.chestlink.upgrade;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads and exposes per-upgrade configuration for toggles and costs.
 */
public class UpgradeSettings {
    private final Map<String, UpgradeConfigEntry> entries;
    private final CapacitySettings capacitySettings;
    private final AutoSortSettings autoSortSettings;
    private final FilterSettings filterSettings;
    private final CompressionSettings compressionSettings;
    private final UpgradeLimitSettings limitSettings;

    public UpgradeSettings(Map<String, UpgradeConfigEntry> entries,
                           CapacitySettings capacitySettings,
                           AutoSortSettings autoSortSettings,
                           FilterSettings filterSettings,
                           CompressionSettings compressionSettings,
                           UpgradeLimitSettings limitSettings) {
        this.entries = entries == null ? Collections.emptyMap() : Collections.unmodifiableMap(entries);
        this.capacitySettings = capacitySettings == null ? CapacitySettings.empty() : capacitySettings;
        this.autoSortSettings = autoSortSettings == null ? AutoSortSettings.defaults() : autoSortSettings;
        this.filterSettings = filterSettings == null ? FilterSettings.defaults() : filterSettings;
        this.compressionSettings = compressionSettings == null ? CompressionSettings.defaults() : compressionSettings;
        this.limitSettings = limitSettings == null ? UpgradeLimitSettings.disabled() : limitSettings;
    }

    public UpgradeConfigEntry get(String key) {
        if (key == null) {
            return null;
        }
        return entries.get(key.toLowerCase(Locale.ENGLISH));
    }

    public boolean isEnabled(String key) {
        UpgradeConfigEntry entry = get(key);
        return entry == null || entry.isEnabled();
    }

    public CapacitySettings getCapacitySettings() {
        return capacitySettings;
    }

    public AutoSortSettings getAutoSortSettings() {
        return autoSortSettings;
    }

    public FilterSettings getFilterSettings() {
        return filterSettings;
    }

    public CompressionSettings getCompressionSettings() {
        return compressionSettings;
    }

    public UpgradeLimitSettings getLimitSettings() {
        return limitSettings;
    }

    public static UpgradeSettings load(File dataFolder, Logger logger) {
        File file = new File(dataFolder, "upgrades.yml");
        if (!file.exists()) {
            return new UpgradeSettings(Collections.emptyMap(), CapacitySettings.empty(), AutoSortSettings.defaults(), FilterSettings.defaults(), CompressionSettings.defaults(), UpgradeLimitSettings.disabled());
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("upgrades");
        if (root == null) {
            return new UpgradeSettings(Collections.emptyMap(), CapacitySettings.empty(), AutoSortSettings.defaults(), FilterSettings.defaults(), CompressionSettings.defaults(), UpgradeLimitSettings.disabled());
        }

        Map<String, UpgradeConfigEntry> entries = new HashMap<>();
        CapacitySettings capacity = CapacitySettings.empty();
        AutoSortSettings autoSort = AutoSortSettings.defaults();
        FilterSettings filterSettings = FilterSettings.defaults();
        CompressionSettings compressionSettings = CompressionSettings.defaults();
        UpgradeLimitSettings limitSettings = UpgradeLimitSettings.disabled();

        for (String upgradeKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(upgradeKey);
            if (section == null) continue;
            String normalizedKey = normalizeKey(upgradeKey);
            boolean enabled = section.getBoolean("enabled", true);

            Map<Integer, UpgradeCost> costs = new HashMap<>();
            ConfigurationSection costsSection = section.getConfigurationSection("costs");
            if (costsSection != null) {
                for (String levelKey : costsSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelKey);
                        UpgradeCost cost = UpgradeCost.fromConfig(costsSection.getConfigurationSection(levelKey), logger, upgradeKey, level);
                        costs.put(level, cost);
                    } catch (NumberFormatException ex) {
                        logger.warning("[ChestLink] Invalid level '" + levelKey + "' in upgrade '" + upgradeKey + "'; expected a number.");
                    }
                }
            }

            if (costs.isEmpty()) {
                logger.warning("[ChestLink] No costs configured for upgrade '" + upgradeKey + "'. Defaulting to free.");
            }
            entries.put(normalizedKey, new UpgradeConfigEntry(enabled, costs));

            if ("capacity".equals(normalizedKey)) {
                capacity = CapacitySettings.fromSection(section, logger);
            } else if ("auto_sort".equals(normalizedKey)) {
                autoSort = AutoSortSettings.fromSection(section, logger);
            } else if ("filter".equals(normalizedKey)) {
                filterSettings = FilterSettings.fromSection(section);
            } else if ("compression".equals(normalizedKey)) {
                compressionSettings = CompressionSettings.fromSection(section, logger);
            }
        }

        ConfigurationSection limitsSection = config.getConfigurationSection("limits");
        if (limitsSection != null) {
            limitSettings = UpgradeLimitSettings.fromSection(limitsSection, logger);
        }

        return new UpgradeSettings(entries, capacity, autoSort, filterSettings, compressionSettings, limitSettings);
    }

    private static String normalizeKey(String key) {
        String normalized = key.toLowerCase(Locale.ENGLISH);
        if ("sorting".equals(normalized)) {
            return "auto_sort";
        }
        return normalized;
    }
}
