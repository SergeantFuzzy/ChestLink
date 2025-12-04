package dev.sergeantfuzzy.chestlink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID owner;
    private final Map<Integer, BoundChest> chests = new HashMap<>();
    private int nextId = 1;
    private Integer pendingRename;

    public PlayerData(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public Collection<BoundChest> getChests() {
        return chests.values();
    }

    public BoundChest getByIdOrName(String input) {
        try {
            int id = Integer.parseInt(input);
            return chests.get(id);
        } catch (NumberFormatException ignored) {
        }
        String lower = input.toLowerCase(Locale.ENGLISH);
        return chests.values().stream()
                .filter(c -> c.getName().toLowerCase(Locale.ENGLISH).equals(lower))
                .findFirst()
                .orElse(null);
    }

    public BoundChest createChest(String name, InventoryType type, org.bukkit.Location location) {
        int id = nextId++;
        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(null, type.getSize(), name);
        String storageId = owner.toString() + "-" + id;
        long now = System.currentTimeMillis();
        BoundChest chest = new BoundChest(storageId, id, owner, name, type, location, now, now, now, inv, new ChestUpgrades());
        chests.put(id, chest);
        return chest;
    }

    public void addChest(BoundChest chest) {
        chests.put(chest.getId(), chest);
        nextId = Math.max(nextId, chest.getId() + 1);
    }

    public void deleteChest(int id) {
        chests.remove(id);
    }

    public int purgeBroken() {
        int removed = 0;
        for (BoundChest chest : new ArrayList<>(chests.values())) {
            if (chest.getLocation() == null || chest.getLocation().getWorld() == null || chest.getInventory() == null) {
                chests.remove(chest.getId());
                removed++;
            }
        }
        return removed;
    }

    public void reindex() {
        List<BoundChest> all = new ArrayList<>(chests.values());
        all.sort(Comparator.comparingInt(BoundChest::getId));
        chests.clear();
        int id = 1;
        for (BoundChest chest : all) {
            String storageId = chest.getOwner().toString() + "-" + id;
            chests.put(id, new BoundChest(storageId, id, chest.getOwner(), chest.getName(), chest.getType(), chest.getLocation(),
                    chest.getCreatedAt(), chest.getLastAccessed(), chest.getLastModified(), chest.getInventory(), chest.getUpgrades().copy()));
            id++;
        }
        nextId = id;
    }

    public int countByType(InventoryType type) {
        int count = 0;
        for (BoundChest chest : chests.values()) {
            if (chest.getType() == type) {
                count++;
            }
        }
        return count;
    }

    public void setPendingRename(Integer id) {
        this.pendingRename = id;
    }

    public Integer getPendingRename() {
        return pendingRename;
    }
}
