package dev.sergeantfuzzy.chestlink.features.filter;

public enum FilterMode {
    WHITELIST,
    BLACKLIST;

    public static FilterMode from(String raw, FilterMode def) {
        if (raw == null) {
            return def;
        }
        try {
            return FilterMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}