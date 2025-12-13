package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.ChestUpgradeType;
import org.bukkit.Material;

public class CompressionUpgrade extends AbstractChestUpgrade {
    public CompressionUpgrade() {
        super(ChestUpgradeType.COMPRESSION, Material.ANVIL);
    }
}
