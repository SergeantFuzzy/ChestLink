package dev.sergeantfuzzy.chestlink.platform.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;

/**
 * Utility methods for positioning around chest blocks (single or double).
 */
public final class ChestPositionUtil {
    private ChestPositionUtil() {
    }

    /**
     * Returns the center point of a chest (or double chest) at the given base block location.
     */
    public static Location center(Location base) {
        if (base == null || base.getWorld() == null) {
            return base;
        }
        Location primaryCenter = base.clone().add(0.5, 0.5, 0.5);
        Block block = base.getBlock();
        if (!(block.getState() instanceof Chest)) {
            return primaryCenter;
        }
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (!(data instanceof org.bukkit.block.data.type.Chest chestData)) {
            return primaryCenter;
        }
        if (chestData.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE) {
            return primaryCenter;
        }
        Block partner = findPartner(block, chestData.getFacing());
        if (partner == null) {
            return primaryCenter;
        }
        Location partnerCenter = partner.getLocation().add(0.5, 0.5, 0.5);
        return primaryCenter.add(partnerCenter).multiply(0.5);
    }

    private static Block findPartner(Block origin, BlockFace facing) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block adjacent = origin.getRelative(face);
            if (!isChest(adjacent)) continue;
            org.bukkit.block.data.BlockData data = adjacent.getBlockData();
            if (!(data instanceof org.bukkit.block.data.type.Chest chest)) continue;
            if (chest.getFacing() != facing) continue;
            return adjacent;
        }
        return null;
    }

    private static boolean isChest(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }
}