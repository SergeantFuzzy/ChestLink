package dev.sergeantfuzzy.chestlink.features.upgrade;

import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;

import java.util.*;

/**
 * Central registry for all chest upgrades. Keeps a stable lookup by key and type for use
 * by GUI, config, and upgrade execution paths.
 */
public class UpgradeRegistry {
    private final Map<String, ChestUpgrade> byKey = new LinkedHashMap<>();
    private final EnumMap<ChestUpgradeType, ChestUpgrade> byType = new EnumMap<>(ChestUpgradeType.class);
    private UpgradeSettings settings;

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

    public void applySettings(UpgradeSettings settings) {
        this.settings = settings;
    }

    public UpgradeConfigEntry configFor(ChestUpgradeType type) {
        if (settings == null || type == null) {
            return null;
        }
        return settings.get(type.getKey());
    }

    public boolean isEnabled(ChestUpgradeType type) {
        if (type == null) return true;
        if (settings == null) return true;
        return settings.isEnabled(type.getKey());
    }

    public Collection<ChestUpgrade> enabledUpgrades() {
        List<ChestUpgrade> upgrades = new ArrayList<>();
        for (ChestUpgrade upgrade : byKey.values()) {
            if (isEnabled(upgrade.getType())) {
                upgrades.add(upgrade);
            }
        }
        return upgrades;
    }

    public void registerDefaults() {
        register(new CapacityUpgrade());
        register(new AutoSortUpgrade());
        register(new FilterUpgrade());
        register(new CompressionUpgrade());
    }
}