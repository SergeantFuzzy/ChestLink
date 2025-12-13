package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.ChestUpgradeType;
import org.bukkit.Material;

public class AutoSortUpgrade extends AbstractChestUpgrade {
    public AutoSortUpgrade() {
        super(ChestUpgradeType.AUTO_SORT, Material.HOPPER);
    }
}
