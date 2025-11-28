package dev.sergeantfuzzy.chestlink;

import dev.sergeantfuzzy.chestlink.lang.MessageService;
import dev.sergeantfuzzy.chestlink.storage.DataStore;
import org.bukkit.Location;
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

    public ChestLinkManager(DataStore dataStore, MessageService messages) {
        this.dataStore = dataStore;
        this.messages = messages;
    }

    public PlayerData getData(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> dataStore.load(id));
    }

    public void save(Player player) {
        PlayerData data = cache.get(player.getUniqueId());
        if (data != null) {
            dataStore.save(data);
        }
    }

    public void saveAll() {
        for (UUID id : cache.keySet()) {
            dataStore.save(cache.get(id));
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
        messages.send(player, "bind-success", Map.of(
                "name", chest.getName(),
                "id", String.valueOf(chest.getId())
        ));
        clearPending(player);
        dataStore.save(data);
        return chest;
    }

    public boolean canCreate(Player player, InventoryType type, int limit) {
        PlayerData data = getData(player);
        return data.countByType(type) < limit;
    }

    public void deleteChest(Player player, BoundChest chest) {
        PlayerData data = getData(player);
        data.deleteChest(chest.getId());
        dataStore.save(data);
    }

    public void resetChest(BoundChest chest) {
        chest.resetInventory();
    }

    public BoundChest getChest(Player player, String input) {
        return getData(player).getByIdOrName(input);
    }

    public BoundChest getChest(Player player, org.bukkit.Location location) {
        if (location == null) return null;
        for (BoundChest chest : getData(player).getChests()) {
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
            dataStore.save(data);
        }
    }

    public int purgeBroken() {
        int removed = 0;
        for (PlayerData data : cache.values()) {
            removed += data.purgeBroken();
            dataStore.save(data);
        }
        return removed;
    }

    public record PendingBind(String name, InventoryType type) {
    }
}
