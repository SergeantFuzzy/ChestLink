package dev.sergeantfuzzy.chestlink.core.data;

import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-chest upgrade state container. Maintains upgrade levels with validation against
 * the definitions in {@link ChestUpgradeType}.
 */
public class ChestUpgrades {
    private final EnumMap<ChestUpgradeType, Integer> levels = new EnumMap<>(ChestUpgradeType.class);

    public ChestUpgrades() {
    }

    public ChestUpgrades(Map<ChestUpgradeType, Integer> levels) {
        if (levels != null) {
            levels.forEach(this::setLevel);
        }
    }

    public int getLevel(ChestUpgradeType type) {
        if (type == null) {
            return 0;
        }
        return levels.getOrDefault(type, 0);
    }

    public boolean isUnlocked(ChestUpgradeType type) {
        return getLevel(type) > 0;
    }

    public void setLevel(ChestUpgradeType type, int level) {
        if (type == null) {
            return;
        }
        int capped = Math.max(0, Math.min(level, type.getMaxLevel()));
        if (capped == 0) {
            levels.remove(type);
        } else {
            levels.put(type, capped);
        }
    }

    public void increment(ChestUpgradeType type) {
        if (type == null) {
            return;
        }
        setLevel(type, Math.min(getLevel(type) + 1, type.getMaxLevel()));
    }

    public Map<ChestUpgradeType, Integer> asMap() {
        return Collections.unmodifiableMap(levels);
    }

    public Map<String, Integer> toSerializable() {
        Map<String, Integer> serialized = new LinkedHashMap<>();
        for (Map.Entry<ChestUpgradeType, Integer> entry : levels.entrySet()) {
            serialized.put(entry.getKey().getKey(), entry.getValue());
        }
        return serialized;
    }

    public static ChestUpgrades fromSerializable(Map<String, Integer> raw) {
        ChestUpgrades upgrades = new ChestUpgrades();
        if (raw != null) {
            for (Map.Entry<String, Integer> entry : raw.entrySet()) {
                ChestUpgradeType type = ChestUpgradeType.fromKey(entry.getKey());
                if (type != null) {
                    upgrades.setLevel(type, entry.getValue() == null ? 0 : entry.getValue());
                }
            }
        }
        return upgrades;
    }

    public ChestUpgrades copy() {
        return new ChestUpgrades(levels);
    }
}