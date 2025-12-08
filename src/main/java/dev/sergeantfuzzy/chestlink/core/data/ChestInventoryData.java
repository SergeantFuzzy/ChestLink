package dev.sergeantfuzzy.chestlink.core.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple storage container for chest contents that supports arbitrary capacities (including > 54).
 * This is intentionally detached from Bukkit's {@link org.bukkit.inventory.Inventory} limits so
 * we can paginate UIs while still tracking the exact configured slot count.
 */
public class ChestInventoryData {
    private ItemStack[] contents;

    public ChestInventoryData(int capacity) {
        this(capacity, null);
    }

    public ChestInventoryData(int capacity, ItemStack[] initial) {
        int safeCapacity = Math.max(1, capacity);
        this.contents = new ItemStack[safeCapacity];
        if (initial != null && initial.length > 0) {
            System.arraycopy(initial, 0, this.contents, 0, Math.min(initial.length, safeCapacity));
        }
    }

    public int getCapacity() {
        return contents.length;
    }

    public ItemStack[] copyContents() {
        return Arrays.copyOf(contents, contents.length);
    }

    public void setContents(ItemStack[] newContents) {
        if (newContents == null) {
            this.contents = new ItemStack[getCapacity()];
            return;
        }
        ItemStack[] target = new ItemStack[getCapacity()];
        System.arraycopy(newContents, 0, target, 0, Math.min(newContents.length, target.length));
        this.contents = target;
    }

    public void resize(int newCapacity) {
        int target = Math.max(1, newCapacity);
        if (target == contents.length) {
            return;
        }
        ItemStack[] resized = new ItemStack[target];
        System.arraycopy(contents, 0, resized, 0, Math.min(contents.length, resized.length));
        this.contents = resized;
    }

    public Map<Integer, ItemStack> addItem(ItemStack... stacks) {
        Map<Integer, ItemStack> leftover = new HashMap<>();
        if (stacks == null) {
            return leftover;
        }
        for (ItemStack input : stacks) {
            if (input == null || input.getType() == Material.AIR) {
                continue;
            }
            ItemStack toInsert = input.clone();
            int remaining = toInsert.getAmount();
            int maxStack = Math.max(1, toInsert.getMaxStackSize());

            // First, try to merge with similar stacks
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot == null || slot.getType() == Material.AIR) {
                    continue;
                }
                if (!slot.isSimilar(toInsert)) {
                    continue;
                }
                int space = maxStack - slot.getAmount();
                if (space <= 0) {
                    continue;
                }
                int add = Math.min(space, remaining);
                slot.setAmount(slot.getAmount() + add);
                remaining -= add;
            }

            // Fill empty slots
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot != null && slot.getType() != Material.AIR) {
                    continue;
                }
                int add = Math.min(maxStack, remaining);
                ItemStack placed = toInsert.clone();
                placed.setAmount(add);
                contents[i] = placed;
                remaining -= add;
            }

            if (remaining > 0) {
                ItemStack left = toInsert.clone();
                left.setAmount(remaining);
                leftover.put(contents.length + leftover.size(), left);
            }
        }
        return leftover;
    }

    public boolean canFullyStore(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return true;
        }
        int remaining = stack.getAmount();
        int maxStack = Math.max(1, stack.getMaxStackSize());

        for (ItemStack slot : contents) {
            if (slot == null || slot.getType() == Material.AIR) {
                remaining -= Math.min(remaining, maxStack);
            } else if (slot.isSimilar(stack)) {
                int space = maxStack - slot.getAmount();
                if (space > 0) {
                    remaining -= Math.min(space, remaining);
                }
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    public int getUsedSlots() {
        int used = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) {
                used++;
            }
        }
        return used;
    }

    public void clear() {
        this.contents = new ItemStack[getCapacity()];
    }
}