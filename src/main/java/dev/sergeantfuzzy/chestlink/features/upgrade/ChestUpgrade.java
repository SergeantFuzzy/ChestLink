package dev.sergeantfuzzy.chestlink.features.upgrade;

import dev.sergeantfuzzy.chestlink.core.data.BoundChest;
import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Contract for a chest upgrade. Concrete implementations should encapsulate upgrade-specific
 * metadata and optional behaviour hooks that can be invoked by the core systems.
 */
public interface ChestUpgrade {
    String getKey();

    ChestUpgradeType getType();

    String getDisplayName();

    String getDescription();

    Material getIcon();

    /**
        Invoked when a chest is opened. Default is no-op.
     */
    default void onOpen(BoundChest chest, Player player) {
    }

    /**
        Invoked when a chest is closed. Default is no-op.
     */
    default void onClose(BoundChest chest, Player player, InventoryView view) {
    }

    /**
        Invoked when an item is inserted into the chest. Default is no-op.
     */
    default void onItemInsert(BoundChest chest, Player player, ItemStack inserted) {
    }
}