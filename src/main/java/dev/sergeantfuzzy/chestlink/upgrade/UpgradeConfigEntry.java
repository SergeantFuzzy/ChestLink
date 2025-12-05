package dev.sergeantfuzzy.chestlink.upgrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-upgrade configuration entry: enabled flag plus per-level costs.
 */
public class UpgradeConfigEntry {
    private final boolean enabled;
    private final Map<Integer, UpgradeCost> levelCosts;

    public UpgradeConfigEntry(boolean enabled, Map<Integer, UpgradeCost> levelCosts) {
        this.enabled = enabled;
        this.levelCosts = levelCosts == null ? Collections.emptyMap() : new LinkedHashMap<>(levelCosts);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<Integer, UpgradeCost> getLevelCosts() {
        return Collections.unmodifiableMap(levelCosts);
    }

    /**
     * Returns the configured cost for the target level. If not explicitly defined, returns the
     * highest lower level cost, or a free cost if none exist.
     */
    public UpgradeCost getCostForLevel(int level) {
        if (levelCosts.containsKey(level)) {
            return levelCosts.get(level);
        }
        int best = -1;
        for (Integer lvl : levelCosts.keySet()) {
            if (lvl <= level && lvl > best) {
                best = lvl;
            }
        }
        if (best != -1) {
            return levelCosts.get(best);
        }
        return UpgradeCost.free();
    }
}
