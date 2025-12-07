package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.FilterMode;
import dev.sergeantfuzzy.chestlink.FilterOverflowBehavior;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuration container for filter upgrade behaviour.
 */
public class FilterSettings {
    private final FilterMode defaultMode;
    private final FilterOverflowBehavior overflowBehavior;
    private final int maxEntries;

    public FilterSettings(FilterMode defaultMode, FilterOverflowBehavior overflowBehavior, int maxEntries) {
        this.defaultMode = defaultMode == null ? FilterMode.WHITELIST : defaultMode;
        this.overflowBehavior = overflowBehavior == null ? FilterOverflowBehavior.RETURN : overflowBehavior;
        this.maxEntries = Math.max(0, maxEntries);
    }

    public static FilterSettings defaults() {
        return new FilterSettings(FilterMode.BLACKLIST, FilterOverflowBehavior.RETURN, 27);
    }

    public FilterMode getDefaultMode() {
        return defaultMode;
    }

    public FilterOverflowBehavior getOverflowBehavior() {
        return overflowBehavior;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public static FilterSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        FilterMode mode = FilterMode.from(section.getString("default-mode"), FilterMode.WHITELIST);
        FilterOverflowBehavior overflow = FilterOverflowBehavior.from(section.getString("overflow-behavior"), FilterOverflowBehavior.RETURN);
        int max = section.getInt("max-entries", 27);
        return new FilterSettings(mode, overflow, max);
    }
}
