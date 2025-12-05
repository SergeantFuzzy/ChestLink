package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.ChestUpgradeType;
import org.bukkit.Material;

public class CapacityUpgrade extends AbstractChestUpgrade {
    public CapacityUpgrade() {
        super(ChestUpgradeType.CAPACITY, Material.CHEST);
    }
}
