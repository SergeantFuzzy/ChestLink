package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.ChestUpgradeType;
import org.bukkit.Material;

public class FilterUpgrade extends AbstractChestUpgrade {
    public FilterUpgrade() {
        super(ChestUpgradeType.FILTER, Material.COMPARATOR);
    }
}
