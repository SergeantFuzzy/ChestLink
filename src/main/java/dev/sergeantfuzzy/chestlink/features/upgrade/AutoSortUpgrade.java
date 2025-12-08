package dev.sergeantfuzzy.chestlink.features.upgrade;

import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;
import org.bukkit.Material;

public class AutoSortUpgrade extends AbstractChestUpgrade {
    public AutoSortUpgrade() {
        super(ChestUpgradeType.AUTO_SORT, Material.HOPPER);
    }
}