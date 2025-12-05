package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.ChestUpgradeType;

import java.util.*;

/**
 * Central registry for all chest upgrades. Keeps a stable lookup by key and type for use
 * by GUI, config, and upgrade execution paths.
 */
public class UpgradeRegistry {
    private final Map<String, ChestUpgrade> byKey = new LinkedHashMap<>();
    private final EnumMap<ChestUpgradeType, ChestUpgrade> byType = new EnumMap<>(ChestUpgradeType.class);

    public void register(ChestUpgrade upgrade) {
        Objects.requireNonNull(upgrade, "upgrade");
        String key = upgrade.getKey().toLowerCase(Locale.ENGLISH);
        if (byKey.containsKey(key)) {
            throw new IllegalStateException("Upgrade already registered for key '" + key + "'");
        }
        if (byType.containsKey(upgrade.getType())) {
            throw new IllegalStateException("Upgrade already registered for type '" + upgrade.getType() + "'");
        }
        byKey.put(key, upgrade);
        byType.put(upgrade.getType(), upgrade);
    }

    public ChestUpgrade get(String key) {
        if (key == null) {
            return null;
        }
        return byKey.get(key.toLowerCase(Locale.ENGLISH));
    }

    public ChestUpgrade get(ChestUpgradeType type) {
        return byType.get(type);
    }

    public Collection<ChestUpgrade> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    public boolean isRegistered(String key) {
        return get(key) != null;
    }

    public boolean isRegistered(ChestUpgradeType type) {
        return get(type) != null;
    }

    public void registerDefaults() {
        register(new CapacityUpgrade());
        register(new AutoSortUpgrade());
        register(new FilterUpgrade());
        register(new CompressionUpgrade());
    }
}
