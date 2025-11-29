package dev.sergeantfuzzy.chestlink;

import dev.sergeantfuzzy.chestlink.lang.MessageService;
import dev.sergeantfuzzy.chestlink.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChestLinkManager {
    private final DataStore dataStore;
    private final MessageService messages;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBind> pendingBinds = new ConcurrentHashMap<>();
    private final Map<Inventory, BoundChest> inventoryLookup = new ConcurrentHashMap<>();

    public ChestLinkManager(DataStore dataStore, MessageService messages) {
        this.dataStore = dataStore;
        this.messages = messages;
    }

    public PlayerData getData(Player player) {
        PlayerData data = cache.computeIfAbsent(player.getUniqueId(), id -> dataStore.load(id));
        registerInventories(data);
        boolean changed = false;
        for (BoundChest chest : data.getChests()) {
            int before = chest.getShared().size();
            chest.pruneExpired();
            if (chest.getShared().size() != before) {
                changed = true;
                saveInventory(chest);
            }
        }
        if (changed) {
            dataStore.save(data, player.getName());
        }
        return data;
    }

    public void save(Player player) {
        PlayerData data = cache.get(player.getUniqueId());
        if (data != null) {
            dataStore.save(data, player.getName());
        }
    }

    public void saveInventory(BoundChest chest) {
        dataStore.saveInventory(chest);
    }

    private void registerInventories(PlayerData data) {
        for (BoundChest chest : data.getChests()) {
            inventoryLookup.putIfAbsent(chest.getInventory(), chest);
        }
    }

    public void saveAll() {
        for (UUID id : cache.keySet()) {
            PlayerData data = cache.get(id);
            if (data == null) {
                continue;
            }
            OfflinePlayer offline = data != null ? org.bukkit.Bukkit.getOfflinePlayer(id) : null;
            dataStore.save(data, offline != null ? offline.getName() : null);
        }
    }

    public void startBind(Player player, String name, InventoryType type) {
        pendingBinds.put(player.getUniqueId(), new PendingBind(name, type));
    }

    public PendingBind getPendingBind(Player player) {
        return pendingBinds.get(player.getUniqueId());
    }

    public void clearPending(Player player) {
        pendingBinds.remove(player.getUniqueId());
    }

    public BoundChest bind(Player player, Location loc) {
        PendingBind bind = pendingBinds.get(player.getUniqueId());
        if (bind == null) {
            return null;
        }
        PlayerData data = getData(player);
        String name = bind.name() == null || bind.name().isEmpty() ? "Chest " + (data.getChests().size() + 1) : bind.name();
        BoundChest chest = data.createChest(name, bind.type(), loc);
        registerInventories(data);
        messages.send(player, "bind-success", Map.of(
                "name", chest.getName(),
                "id", String.valueOf(chest.getId())
        ));
        clearPending(player);
        dataStore.save(data, player.getName());
        return chest;
    }

    public boolean canCreate(Player player, InventoryType type, int limit) {
        PlayerData data = getData(player);
        return data.countByType(type) < limit;
    }

    public void deleteChest(Player player, BoundChest chest) {
        PlayerData data = getData(player);
        data.deleteChest(chest.getId());
        inventoryLookup.remove(chest.getInventory());
        dataStore.deleteInventory(chest.getStorageId());
        dataStore.save(data, player.getName());
    }

    public void resetChest(BoundChest chest) {
        chest.resetInventory();
        chest.markModified();
        saveInventory(chest);
    }

    public BoundChest getOwnedChest(Player player, String input) {
        return getData(player).getByIdOrName(input);
    }

    public BoundChest getAccessibleChest(Player player, String input) {
        for (BoundChest chest : getAccessibleChests(player)) {
            if (String.valueOf(chest.getId()).equalsIgnoreCase(input) || chest.getName().equalsIgnoreCase(input)) {
                return chest;
            }
        }
        return null;
    }

    public BoundChest getChest(Player player, org.bukkit.Location location) {
        if (location == null) return null;
        for (BoundChest chest : getAccessibleChests(player)) {
            if (chest.matches(location)) {
                return chest;
            }
        }
        return null;
    }

    public void sortInventory(Inventory inventory) {
        ItemStack[] contents = Arrays.stream(inventory.getContents())
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ItemStack::getType).thenComparing(ItemStack::getAmount))
                .toArray(ItemStack[]::new);
        inventory.clear();
        inventory.addItem(contents);
    }

    public void migrateAll() {
        for (PlayerData data : cache.values()) {
            data.reindex();
            dataStore.save(data, null);
        }
    }

    public int purgeBroken() {
        int removed = 0;
        for (PlayerData data : cache.values()) {
            removed += data.purgeBroken();
            dataStore.save(data, null);
        }
        return removed;
    }

    public List<BoundChest> getAccessibleChests(Player player) {
        List<BoundChest> chests = new ArrayList<>(getData(player).getChests());
        List<BoundChest> shared = dataStore.loadShared(player.getUniqueId());
        for (BoundChest chest : shared) {
            int before = chest.getShared().size();
            chest.pruneExpired();
            if (chest.getShared().size() != before) {
                saveInventory(chest);
            }
        }
        chests.addAll(shared);
        chests.sort(Comparator.comparingInt(BoundChest::getId));
        for (BoundChest chest : chests) {
            inventoryLookup.putIfAbsent(chest.getInventory(), chest);
        }
        return chests;
    }

    public boolean canView(Player player, BoundChest chest) {
        if (player == null || chest == null) return false;
        if (player.hasPermission("chestlink.admin.open")) return true;
        return chest.canView(player.getUniqueId());
    }

    public boolean canModify(Player player, BoundChest chest) {
        if (player == null || chest == null) return false;
        if (player.hasPermission("chestlink.admin.open")) return true;
        return chest.canModify(player.getUniqueId());
    }

    public BoundChest getByInventory(Inventory inventory) {
        return inventoryLookup.get(inventory);
    }

    public void shareChest(BoundChest chest, UUID target, AccessLevel level, Long expires) {
        chest.setSharedAccess(target, new SharedAccess(level, expires));
        chest.markModified();
        saveInventory(chest);
    }

    public void removeShare(BoundChest chest, UUID target) {
        chest.setSharedAccess(target, null);
        saveInventory(chest);
    }

    public record PendingBind(String name, InventoryType type) {
    }
}
