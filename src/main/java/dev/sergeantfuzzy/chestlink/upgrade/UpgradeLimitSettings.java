package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.ChestUpgradeType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Rank-based upgrade limit configuration. Allows servers to gate max upgrade counts
 * and per-upgrade max levels based on permission-backed ranks.
 */
public class UpgradeLimitSettings {
    private final boolean enabled;
    private final List<RankLimit> ranks;

    public UpgradeLimitSettings(boolean enabled, List<RankLimit> ranks) {
        this.enabled = enabled && ranks != null && !ranks.isEmpty();
        this.ranks = this.enabled ? List.copyOf(ranks) : Collections.emptyList();
    }

    public static UpgradeLimitSettings disabled() {
        return new UpgradeLimitSettings(false, Collections.emptyList());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canBypass(Player player) {
        if (!enabled) {
            return true;
        }
        RankLimit rank = match(player);
        return rank != null && rank.bypass();
    }

    public int maxUpgradedChests(Player player) {
        if (!enabled) {
            return -1;
        }
        RankLimit rank = match(player);
        return rank == null ? -1 : rank.maxUpgradedChests();
    }

    public int maxLevel(Player player, ChestUpgradeType type) {
        if (!enabled || type == null) {
            return -1;
        }
        RankLimit rank = match(player);
        if (rank == null) {
            return -1;
        }
        return rank.maxLevels().getOrDefault(type, -1);
    }

    private RankLimit match(Player player) {
        if (!enabled || player == null) {
            return null;
        }
        RankLimit fallback = null;
        for (RankLimit rank : ranks) {
            if (rank.permission() == null || rank.permission().isBlank()) {
                if (fallback == null) {
                    fallback = rank;
                }
                continue;
            }
            if (player.hasPermission(rank.permission())) {
                return rank;
            }
        }
        return fallback;
    }

    public static UpgradeLimitSettings fromSection(ConfigurationSection section, Logger logger) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return UpgradeLimitSettings.disabled();
        }
        ConfigurationSection ranksSection = section.getConfigurationSection("ranks");
        if (ranksSection == null) {
            return UpgradeLimitSettings.disabled();
        }
        List<RankLimit> ranks = new ArrayList<>();
        for (String key : ranksSection.getKeys(false)) {
            ConfigurationSection rankSection = ranksSection.getConfigurationSection(key);
            if (rankSection == null) {
                continue;
            }
            String permission = rankSection.getString("permission", "");
            boolean bypass = rankSection.getBoolean("bypass-limits", false);
            int maxChests = rankSection.getInt("max-upgraded-chests", -1);
            Map<ChestUpgradeType, Integer> maxLevels = new EnumMap<>(ChestUpgradeType.class);
            ConfigurationSection maxLevelSection = rankSection.getConfigurationSection("max-levels");
            if (maxLevelSection != null) {
                for (String upgradeKey : maxLevelSection.getKeys(false)) {
                    ChestUpgradeType type = ChestUpgradeType.fromKey(upgradeKey.toLowerCase(Locale.ENGLISH));
                    if (type == null) {
                        logger.warning("[ChestLink] Unknown upgrade type '" + upgradeKey + "' in limits for '" + key + "'.");
                        continue;
                    }
                    int value = maxLevelSection.getInt(upgradeKey, -1);
                    if (value > 0) {
                        maxLevels.put(type, value);
                    }
                }
            }
            ranks.add(new RankLimit(key, permission, bypass, maxChests, Collections.unmodifiableMap(maxLevels)));
        }
        return new UpgradeLimitSettings(true, ranks);
    }

    public record RankLimit(String key, String permission, boolean bypass, int maxUpgradedChests,
                            Map<ChestUpgradeType, Integer> maxLevels) {
    }
}
