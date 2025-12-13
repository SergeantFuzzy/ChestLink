package dev.sergeantfuzzy.chestlink.features.upgrade;

import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;
import org.bukkit.Material;

public class FilterUpgrade extends AbstractChestUpgrade {
    public FilterUpgrade() {
        super(ChestUpgradeType.FILTER, Material.COMPARATOR);
    }
}