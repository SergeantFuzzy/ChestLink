package dev.sergeantfuzzy.chestlink.features.upgrade;

import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;
import org.bukkit.Material;

public class CapacityUpgrade extends AbstractChestUpgrade {
    public CapacityUpgrade() {
        super(ChestUpgradeType.CAPACITY, Material.CHEST);
    }
}