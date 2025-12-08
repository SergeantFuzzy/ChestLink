package dev.sergeantfuzzy.chestlink.features.upgrade;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Represents the cost to unlock or level up an upgrade. Supports economy, XP, items, or a mix.
 */
public class UpgradeCost {
    private final UpgradeCostType type;
    private final double economyAmount;
    private final int xpLevels;
    private final Map<Material, Integer> itemCosts;

    private UpgradeCost(UpgradeCostType type, double economyAmount, int xpLevels, Map<Material, Integer> itemCosts) {
        this.type = type;
        this.economyAmount = economyAmount;
        this.xpLevels = xpLevels;
        this.itemCosts = itemCosts == null ? Collections.emptyMap() : Collections.unmodifiableMap(itemCosts);
    }

    public static UpgradeCost free() {
        return new UpgradeCost(UpgradeCostType.ECONOMY, 0, 0, Collections.emptyMap());
    }

    public static UpgradeCost economy(double amount) {
        return new UpgradeCost(UpgradeCostType.ECONOMY, Math.max(0, amount), 0, Collections.emptyMap());
    }

    public static UpgradeCost xp(int levels) {
        return new UpgradeCost(UpgradeCostType.XP, 0, Math.max(0, levels), Collections.emptyMap());
    }

    public static UpgradeCost items(Map<Material, Integer> items) {
        return new UpgradeCost(UpgradeCostType.ITEMS, 0, 0, normalizedItems(items));
    }

    public static UpgradeCost mixed(double economyAmount, int xpLevels, Map<Material, Integer> items) {
        return new UpgradeCost(UpgradeCostType.MIXED, Math.max(0, economyAmount), Math.max(0, xpLevels), normalizedItems(items));
    }

    public UpgradeCostType getType() {
        return type;
    }

    public double getEconomyAmount() {
        return economyAmount;
    }

    public int getXpLevels() {
        return xpLevels;
    }

    public Map<Material, Integer> getItemCosts() {
        return itemCosts;
    }

    public boolean hasEconomy() {
        return economyAmount > 0;
    }

    public boolean hasXp() {
        return xpLevels > 0;
    }

    public boolean hasItems() {
        return !itemCosts.isEmpty();
    }

    public boolean isFree() {
        return !hasEconomy() && !hasXp() && !hasItems();
    }

    public static UpgradeCost fromConfig(ConfigurationSection section, Logger logger, String upgradeKey, int level) {
        if (section == null) {
            return UpgradeCost.free();
        }
        UpgradeCostType type = UpgradeCostType.from(section.getString("type", "economy"));
        double economy = section.getDouble("amount", 0);
        int xp = section.getInt("xp-levels", type == UpgradeCostType.XP ? (int) section.getDouble("amount", 0) : 0);
        Map<Material, Integer> items = readItems(section.getConfigurationSection("items"), logger, upgradeKey, level);

        UpgradeCost cost;
        switch (type) {
            case ITEMS -> cost = UpgradeCost.items(items);
            case XP -> cost = UpgradeCost.xp(xp);
            case MIXED -> cost = UpgradeCost.mixed(economy, xp, items);
            default -> cost = UpgradeCost.economy(economy);
        }

        if (cost.isFree()) {
            logger.warning("[ChestLink] Upgrade '" + upgradeKey + "' level " + level + " has a zero or empty cost; treating as free.");
        }
        return cost;
    }

    private static Map<Material, Integer> readItems(ConfigurationSection section, Logger logger, String upgradeKey, int level) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<Material, Integer> items = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ENGLISH));
            if (material == null) {
                logger.warning("[ChestLink] Unknown material '" + key + "' in upgrade '" + upgradeKey + "' level " + level + " items cost; skipping.");
                continue;
            }
            int amount = Math.max(1, section.getInt(key, 1));
            items.put(material, amount);
        }
        return items;
    }

    private static Map<Material, Integer> normalizedItems(Map<Material, Integer> items) {
        if (items == null) return Collections.emptyMap();
        Map<Material, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            if (entry.getKey() == null) continue;
            result.put(entry.getKey(), Math.max(1, Objects.requireNonNullElse(entry.getValue(), 1)));
        }
        return result;
    }
}