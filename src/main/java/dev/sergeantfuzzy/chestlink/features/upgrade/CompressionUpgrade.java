package dev.sergeantfuzzy.chestlink.features.upgrade;

import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;
import org.bukkit.Material;

public class CompressionUpgrade extends AbstractChestUpgrade {
    public CompressionUpgrade() {
        super(ChestUpgradeType.COMPRESSION, Material.ANVIL);
    }
}