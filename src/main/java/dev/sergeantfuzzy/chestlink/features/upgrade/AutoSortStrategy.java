package dev.sergeantfuzzy.chestlink.features.upgrade;

/**
 * Sorting strategies supported by the auto-sort upgrade.
 */
public enum AutoSortStrategy {
    ALPHABETICAL;

    public static AutoSortStrategy from(String raw) {
        if (raw == null) {
            return ALPHABETICAL;
        }
        return switch (raw.trim().toLowerCase()) {
            case "alphabet", "alphabetical", "alpha" -> ALPHABETICAL;
            default -> ALPHABETICAL;
        };
    }
}