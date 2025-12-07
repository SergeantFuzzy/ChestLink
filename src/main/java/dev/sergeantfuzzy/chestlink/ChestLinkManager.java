package dev.sergeantfuzzy.chestlink;

import dev.sergeantfuzzy.chestlink.FilterOverflowBehavior;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import dev.sergeantfuzzy.chestlink.upgrade.AutoSortStrategy;
import dev.sergeantfuzzy.chestlink.upgrade.CapacitySettings;
import dev.sergeantfuzzy.chestlink.upgrade.CompressionSettings;
import dev.sergeantfuzzy.chestlink.upgrade.FilterSettings;
import dev.sergeantfuzzy.chestlink.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChestLinkManager {
    private final ChestLinkPlugin plugin;
    private final DataStore dataStore;
    private final MessageService messages;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBind> pendingBinds = new ConcurrentHashMap<>();
    private final Map<Inventory, BoundChest> inventoryLookup = new ConcurrentHashMap<>();
    private final Set<String> pendingAutoSort = ConcurrentHashMap.newKeySet();

    public ChestLinkManager(ChestLinkPlugin plugin, DataStore dataStore, MessageService messages) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.messages = messages;
    }

    public PlayerData getData(Player player) {
        PlayerData data = cache.computeIfAbsent(player.getUniqueId(), id -> dataStore.load(id));
        registerInventories(data);
        boolean changed = false;
        for (BoundChest chest : data.getChests()) {
            if (applyCapacity(chest)) {
                changed = true;
                saveInventory(chest);
            }
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
        sortInventory(inventory, plugin.upgradeSettings().getAutoSortSettings().getStrategy());
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
            if (applyCapacity(chest)) {
                saveInventory(chest);
            }
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

    public boolean enforceFilter(BoundChest chest, Player player) {
        if (chest == null || chest.getUpgradeLevel(ChestUpgradeType.FILTER) <= 0) {
            return false;
        }
        ChestFilter filter = chest.getFilter();
        if (filter == null) {
            return false;
        }
        Inventory inv = chest.getInventory();
        ItemStack[] contents = inv.getContents();
        List<ItemStack> rejected = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!filter.allows(stack.getType())) {
                rejected.add(stack.clone());
                inv.clear(i);
            }
        }
        if (rejected.isEmpty()) {
            return false;
        }
        FilterSettings settings = plugin.upgradeSettings().getFilterSettings();
        FilterOverflowBehavior behavior = settings.getOverflowBehavior();
        handleOverflow(player, chest, rejected, behavior, "filter-dropped", "filter-returned", "filter-blocked");
        return true;
    }

    public boolean applyCompression(BoundChest chest, Player player) {
        if (chest == null || chest.getUpgradeLevel(ChestUpgradeType.COMPRESSION) <= 0) {
            return false;
        }
        CompressionSettings settings = plugin.upgradeSettings().getCompressionSettings();
        if (settings.recipes().isEmpty()) {
            return false;
        }
        Inventory inventory = chest.getInventory();
        boolean changed = false;
        List<ItemStack> overflow = new ArrayList<>();
        for (Map.Entry<Material, Material> recipe : settings.recipes().entrySet()) {
            Material input = recipe.getKey();
            Material output = recipe.getValue();
            if (input == null || output == null) {
                continue;
            }
            int total = countMaterial(inventory, input);
            int craftable = total / 9;
            if (craftable <= 0) {
                continue;
            }
            consumeMaterial(inventory, input, craftable * 9);
            addOutputs(inventory, output, craftable, overflow);
            changed = true;
        }
        if (changed) {
            chest.markModified();
        }
        if (!overflow.isEmpty()) {
            handleOverflow(player, chest, overflow, settings.overflowBehavior(), "compression-dropped", "compression-returned", "compression-blocked");
        }
        return changed;
    }

    public void scheduleAutoSort(BoundChest chest) {
        if (!shouldAutoSort(chest)) {
            return;
        }
        if (!pendingAutoSort.add(chest.getStorageId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingAutoSort.remove(chest.getStorageId());
            if (applyAutoSort(chest)) {
                saveInventory(chest);
            }
        }, 1L);
    }

    public boolean applyAutoSort(BoundChest chest) {
        if (!shouldAutoSort(chest)) {
            return false;
        }
        return sortInventory(chest.getInventory(), plugin.upgradeSettings().getAutoSortSettings().getStrategy());
    }

    public boolean applyCapacity(BoundChest chest) {
        if (chest == null) {
            return false;
        }
        CapacitySettings settings = plugin.upgradeSettings() != null ? plugin.upgradeSettings().getCapacitySettings() : CapacitySettings.empty();
        int level = chest.getUpgradeLevel(ChestUpgradeType.CAPACITY);
        int desired = settings.sizeForLevel(level, chest.getType().getSize());
        if (desired <= chest.getInventory().getSize()) {
            return false;
        }
        Inventory old = chest.getInventory();
        Inventory resized;
        try {
            resized = Bukkit.createInventory(null, desired, chest.getName());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[ChestLink] Failed to expand inventory for chest #" + chest.getId() + " to " + desired + " slots: " + ex.getMessage());
            return false;
        }
        ItemStack[] contents = old.getContents();
        resized.setContents(Arrays.copyOf(contents, desired));
        chest.setInventory(resized);
        inventoryLookup.remove(old);
        inventoryLookup.put(resized, chest);
        chest.markModified();
        return true;
    }

    private boolean sortInventory(Inventory inventory, AutoSortStrategy strategy) {
        if (inventory == null || strategy == null) {
            return false;
        }
        ItemStack[] contents = inventory.getContents();
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            stacks.add(stack);
        }
        if (stacks.isEmpty()) {
            return false;
        }
        stacks.sort(comparatorFor(strategy));
        ItemStack[] sorted = new ItemStack[inventory.getSize()];
        for (int i = 0; i < sorted.length && i < stacks.size(); i++) {
            sorted[i] = stacks.get(i);
        }
        inventory.setContents(sorted);
        return true;
    }

    private Comparator<ItemStack> comparatorFor(AutoSortStrategy strategy) {
        return switch (strategy) {
            case ALPHABETICAL -> ALPHABETICAL_COMPARATOR;
        };
    }

    private static final Comparator<ItemStack> ALPHABETICAL_COMPARATOR = Comparator
            .comparing(ChestLinkManager::stackLabel, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(stack -> stack.getType().name())
            .thenComparingInt(ItemStack::getAmount);

    private static String stackLabel(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        if (stack.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return ChatColor.stripColor(meta.getDisplayName());
            }
        }
        return stack.getType().name().toLowerCase(Locale.ENGLISH);
    }

    private boolean shouldAutoSort(BoundChest chest) {
        return chest != null && chest.getUpgradeLevel(ChestUpgradeType.AUTO_SORT) > 0;
    }

    private void handleOverflow(Player player, BoundChest chest, List<ItemStack> items, FilterOverflowBehavior behavior,
                                String dropMessage, String returnMessage, String denyMessage) {
        if (items.isEmpty()) {
            return;
        }
        switch (behavior) {
            case DROP -> {
                dropItems(player, chest, items);
                if (player != null && dropMessage != null) {
                    messages.send(player, dropMessage, null);
                }
            }
            case RETURN -> {
                returnItems(player, chest, items);
                if (player != null && returnMessage != null) {
                    messages.send(player, returnMessage, null);
                }
            }
            case DENY -> {
                returnItems(player, chest, items);
                if (player != null && denyMessage != null) {
                    messages.send(player, denyMessage, null);
                }
            }
        }
    }

    private int countMaterial(Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void consumeMaterial(Inventory inventory, Material material, int amount) {
        if (amount <= 0) return;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int remove = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - remove);
            amount -= remove;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        inventory.setContents(contents);
    }

    private void addOutputs(Inventory inventory, Material output, int count, List<ItemStack> overflow) {
        int maxStack = Math.max(1, output.getMaxStackSize());
        int remaining = count;
        while (remaining > 0) {
            int batch = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(output, batch);
            Map<Integer, ItemStack> extras = inventory.addItem(stack);
            if (!extras.isEmpty()) {
                overflow.addAll(extras.values());
            }
            remaining -= batch;
        }
    }

    private void returnItems(Player player, BoundChest chest, List<ItemStack> stacks) {
        if (player == null) {
            dropItems(null, chest, stacks);
            return;
        }
        Map<Integer, ItemStack> leftovers = new HashMap<>();
        for (ItemStack stack : stacks) {
            Map<Integer, ItemStack> result = player.getInventory().addItem(stack);
            leftovers.putAll(result);
        }
        if (!leftovers.isEmpty()) {
            dropItems(player, chest, new ArrayList<>(leftovers.values()));
        }
    }

    private void dropItems(Player player, BoundChest chest, List<ItemStack> stacks) {
        Location location = dropLocation(player, chest);
        if (location == null || location.getWorld() == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            location.getWorld().dropItemNaturally(location, stack);
        }
    }

    private Location dropLocation(Player player, BoundChest chest) {
        if (player != null && player.isOnline()) {
            return player.getLocation();
        }
        if (chest != null && chest.getLocation() != null) {
            return chest.getLocation().clone().add(0.5, 1, 0.5);
        }
        return plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0).getSpawnLocation();
    }

    public record PendingBind(String name, InventoryType type) {
    }
}
