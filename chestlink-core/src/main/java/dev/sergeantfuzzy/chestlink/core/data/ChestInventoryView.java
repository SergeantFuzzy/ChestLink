package dev.sergeantfuzzy.chestlink.core.data;

import org.bukkit.inventory.Inventory;

import java.util.Set;

/**
 * Context object representing an open inventory page for a bound chest.
 * Holds layout metadata so listeners can route navigation clicks and block filler slots.
 */
public record ChestInventoryView(
        BoundChest chest,
        int pageIndex,
        int totalPages,
        Inventory inventory,
        Set<Integer> blockedSlots,
        int prevSlot,
        int infoSlot,
        int nextSlot
) {
    public boolean isBlocked(int slot) {
        return blockedSlots.contains(slot);
    }

    public boolean isPrev(int slot) {
        return prevSlot >= 0 && slot == prevSlot;
    }

    public boolean isNext(int slot) {
        return nextSlot >= 0 && slot == nextSlot;
    }

    public boolean isInfo(int slot) {
        return infoSlot >= 0 && slot == infoSlot;
    }
}