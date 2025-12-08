package dev.sergeantfuzzy.chestlink.features.filter;

public enum FilterOverflowBehavior {
    DROP,
    RETURN,
    DENY;

    public static FilterOverflowBehavior from(String raw, FilterOverflowBehavior def) {
        if (raw == null) {
            return def;
        }
        try {
            return FilterOverflowBehavior.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}