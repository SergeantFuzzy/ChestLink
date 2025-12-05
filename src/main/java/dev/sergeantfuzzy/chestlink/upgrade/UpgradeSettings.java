package dev.sergeantfuzzy.chestlink.upgrade;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads and exposes per-upgrade configuration for toggles and costs.
 */
public class UpgradeSettings {
    private final Map<String, UpgradeConfigEntry> entries;

    public UpgradeSettings(Map<String, UpgradeConfigEntry> entries) {
        this.entries = entries;
    }

    public UpgradeConfigEntry get(String key) {
        if (key == null) return null;
        return entries.get(key.toLowerCase(Locale.ENGLISH));
    }

    public boolean isEnabled(String key) {
        UpgradeConfigEntry entry = get(key);
        return entry == null || entry.isEnabled();
    }

    public static UpgradeSettings load(File dataFolder, Logger logger) {
        File file = new File(dataFolder, "upgrades.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("upgrades");
        if (root == null) {
            root = config;
        }

        Map<String, UpgradeConfigEntry> entries = new HashMap<>();

        for (String upgradeKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(upgradeKey);
            if (section == null) continue;
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
            entries.put(upgradeKey.toLowerCase(Locale.ENGLISH), new UpgradeConfigEntry(enabled, costs));
        }

        return new UpgradeSettings(entries);
    }
}
