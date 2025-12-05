package dev.sergeantfuzzy.chestlink.upgrade;

public enum UpgradeCostType {
    ECONOMY,
    ITEMS,
    XP,
    MIXED;

    public static UpgradeCostType from(String raw) {
        if (raw == null) return ECONOMY;
        try {
            return UpgradeCostType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ECONOMY;
        }
    }
}
