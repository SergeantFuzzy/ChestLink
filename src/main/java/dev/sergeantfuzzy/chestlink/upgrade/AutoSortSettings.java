package dev.sergeantfuzzy.chestlink.upgrade;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 * Settings wrapper for the auto-sort upgrade.
 */
public class AutoSortSettings {
    private final AutoSortStrategy strategy;

    public AutoSortSettings(AutoSortStrategy strategy) {
        this.strategy = strategy == null ? AutoSortStrategy.ALPHABETICAL : strategy;
    }

    public static AutoSortSettings defaults() {
        return new AutoSortSettings(AutoSortStrategy.ALPHABETICAL);
    }

    public AutoSortStrategy getStrategy() {
        return strategy;
    }

    public static AutoSortSettings fromSection(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return defaults();
        }
        String raw = section.getString("strategy", "alphabetical");
        AutoSortStrategy strategy = AutoSortStrategy.from(raw);
        if (strategy == AutoSortStrategy.ALPHABETICAL && raw != null && raw.trim().isEmpty()) {
            logger.warning("[ChestLink] Auto-sort strategy was blank; defaulting to alphabetical.");
        }
        return new AutoSortSettings(strategy);
    }
}
