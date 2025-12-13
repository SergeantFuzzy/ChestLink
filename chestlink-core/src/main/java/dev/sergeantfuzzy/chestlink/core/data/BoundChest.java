package dev.sergeantfuzzy.chestlink.core.data;

import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.features.filter.ChestFilter;
import dev.sergeantfuzzy.chestlink.features.filter.FilterMode;
import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;
import dev.sergeantfuzzy.chestlink.features.upgrade.FilterSettings;
import dev.sergeantfuzzy.chestlink.localization.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.*;

public class BoundChest {
    private final String storageId;
    private final int id;
    private String name;
    private final UUID owner;
    private final InventoryType type;
    private Location location;
    private final long createdAt;
    private long lastAccessed;
    private long lastModified;
    private ChestInventoryData inventory;
    private final ChestUpgrades upgrades;
    private ChestFilter filter;
    private final Map<UUID, SharedAccess> shared = new HashMap<>();

    public BoundChest(String storageId, int id, UUID owner, String name, InventoryType type, Location location, long createdAt, long lastAccessed, long lastModified, Inventory inventory) {
        this(storageId, id, owner, name, type, location, createdAt, lastAccessed, lastModified,
                inventory != null ? new ChestInventoryData(inventory.getSize(), inventory.getContents()) : new ChestInventoryData(type.getSize()),
                new ChestUpgrades(), null);
    }

    public BoundChest(String storageId, int id, UUID owner, String name, InventoryType type, Location location, long createdAt, long lastAccessed, long lastModified, Inventory inventory, ChestUpgrades upgrades) {
        this(storageId, id, owner, name, type, location, createdAt, lastAccessed, lastModified,
                inventory != null ? new ChestInventoryData(inventory.getSize(), inventory.getContents()) : new ChestInventoryData(type.getSize()),
                upgrades, null);
    }

    public BoundChest(String storageId, int id, UUID owner, String name, InventoryType type, Location location,
                      long createdAt, long lastAccessed, long lastModified, ChestInventoryData inventory,
                      ChestUpgrades upgrades, ChestFilter filter) {
        this.storageId = storageId;
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.location = location;
        this.createdAt = createdAt;
        this.lastAccessed = lastAccessed;
        this.lastModified = lastModified;
        this.inventory = inventory != null ? inventory : new ChestInventoryData(type.getSize());
        this.upgrades = upgrades != null ? upgrades : new ChestUpgrades();
        this.filter = filter != null ? filter : defaultFilter();
    }

    public String getStorageId() {
        return storageId;
    }

    public int getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Renames the chest. The display title is applied when building a UI page.
     */
    public void renameInventory(String newName) {
        this.name = newName;
    }

    public InventoryType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccessed() {
        return lastAccessed;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void markModified() {
        this.lastModified = Instant.now().toEpochMilli();
        markAccessed();
    }

    public void markAccessed() {
        this.lastAccessed = Instant.now().toEpochMilli();
    }

    public ChestInventoryData getInventoryData() {
        return inventory;
    }

    public void setInventory(ChestInventoryData inventory) {
        this.inventory = inventory;
    }

    public ChestUpgrades getUpgrades() {
        return upgrades;
    }

    public ChestFilter getFilter() {
        if (filter == null) {
            filter = defaultFilter();
        }
        return filter;
    }

    public void setFilter(ChestFilter filter) {
        this.filter = filter != null ? filter : defaultFilter();
        markModified();
    }

    public int getUpgradeLevel(ChestUpgradeType type) {
        return upgrades.getLevel(type);
    }

    public void setUpgradeLevel(ChestUpgradeType type, int level) {
        upgrades.setLevel(type, level);
        markModified();
    }

    public boolean hasUpgrade(ChestUpgradeType type) {
        return upgrades.isUnlocked(type);
    }

    public void resetInventory() {
        inventory.clear();
    }

    public void deleteInventory() {
        inventory.clear();
    }

    public boolean matches(Location other) {
        if (other == null || location == null) {
            return false;
        }
        if (!Objects.equals(location.getWorld(), other.getWorld())) {
            return false;
        }
        if (location.getBlockX() == other.getBlockX()
                && location.getBlockY() == other.getBlockY()
                && location.getBlockZ() == other.getBlockZ()) {
            return true;
        }
        if (type != InventoryType.DOUBLE) {
            return false;
        }
        // Allow either half of a double chest to match
        int dx = Math.abs(location.getBlockX() - other.getBlockX());
        int dz = Math.abs(location.getBlockZ() - other.getBlockZ());
        return location.getBlockY() == other.getBlockY() && dx + dz == 1;
    }

    public ConfigurationSection serialize(ConfigurationSection section) {
        section.set("id", id);
        section.set("name", name);
        section.set("owner", owner.toString());
        section.set("type", type.name());
        section.set("lastModified", lastModified);
        if (location != null) {
            section.set("world", location.getWorld() != null ? location.getWorld().getName() : null);
            section.set("x", location.getBlockX());
            section.set("y", location.getBlockY());
            section.set("z", location.getBlockZ());
        }
        section.set("created", createdAt);
        section.set("lastAccessed", lastAccessed);
        section.set("contents", inventory.copyContents());
        Map<String, Integer> upgradeMap = upgrades.toSerializable();
        if (!upgradeMap.isEmpty()) {
            ConfigurationSection upgradesSection = section.createSection("upgrades");
            for (Map.Entry<String, Integer> entry : upgradeMap.entrySet()) {
                upgradesSection.set(entry.getKey(), entry.getValue());
            }
        }
        ChestFilter chestFilter = getFilter();
        if (chestFilter != null) {
            ConfigurationSection filterSection = section.createSection("filter");
            filterSection.set("mode", chestFilter.getMode().name());
            filterSection.set("items", chestFilter.serialize());
        }
        ConfigurationSection sharedSection = section.createSection("shared");
        for (Map.Entry<UUID, SharedAccess> entry : shared.entrySet()) {
            ConfigurationSection s = sharedSection.createSection(entry.getKey().toString());
            s.set("access", entry.getValue().getAccessLevel().name());
            s.set("expires", entry.getValue().getExpiresAt());
        }
        return section;
    }

    public static BoundChest from(ConfigurationSection section, UUID owner) {
        int id = section.getInt("id");
        String name = section.getString("name", "Chest " + id);
        InventoryType type = InventoryType.valueOf(section.getString("type", InventoryType.SINGLE.name()));
        String worldName = section.getString("world", null);
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        Location loc = null;
        if (world != null) {
            loc = new Location(world, section.getInt("x"), section.getInt("y"), section.getInt("z"));
        }
        long created = section.getLong("created", Instant.now().toEpochMilli());
        long lastAccess = section.getLong("lastAccessed", created);
        long lastModified = section.getLong("lastModified", lastAccess);
        List<ItemStack> contents = new ArrayList<>(Collections.nCopies(type.getSize(), null));
        List<?> raw = section.getList("contents");
        if (raw != null) {
            for (int i = 0; i < Math.min(raw.size(), contents.size()); i++) {
                Object o = raw.get(i);
                if (o instanceof ItemStack) {
                    contents.set(i, (ItemStack) o);
                }
            }
        }
        ItemStack[] contentArray = contents.toArray(new ItemStack[0]);
        String storageId = owner.toString() + "-" + id;
        Map<String, Integer> upgradesRaw = new HashMap<>();
        ConfigurationSection upgradesSection = section.getConfigurationSection("upgrades");
        if (upgradesSection != null) {
            for (String key : upgradesSection.getKeys(false)) {
                upgradesRaw.put(key, upgradesSection.getInt(key));
            }
        }
        ChestUpgrades upgrades = ChestUpgrades.fromSerializable(upgradesRaw);
        ChestFilter filter = readFilter(section);
        ChestInventoryData invData = new ChestInventoryData(Math.max(type.getSize(), contentArray.length), contentArray);
        BoundChest bound = new BoundChest(storageId, id, owner, name, type, loc, created, lastAccess, lastModified, invData, upgrades, filter);
        ConfigurationSection sharedSection = section.getConfigurationSection("shared");
        if (sharedSection != null) {
            for (String key : sharedSection.getKeys(false)) {
                ConfigurationSection s = sharedSection.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    UUID target = UUID.fromString(key);
                    AccessLevel level = AccessLevel.valueOf(s.getString("access", AccessLevel.VIEW.name()));
                    Long expires = s.contains("expires") ? s.getLong("expires") : null;
                    bound.shared.put(target, new SharedAccess(level, expires));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        bound.pruneExpired();
        return bound;
    }

    private static ChestFilter readFilter(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        ConfigurationSection filterSection = section.getConfigurationSection("filter");
        if (filterSection == null) {
            return null;
        }
        FilterMode mode = FilterMode.from(filterSection.getString("mode", null),
                ChestLinkPlugin.get().upgradeSettings().getFilterSettings().getDefaultMode());
        List<String> rawItems = filterSection.getStringList("items");
        return ChestFilter.fromSerialized(mode, rawItems);
    }

    public Map<UUID, SharedAccess> getShared() {
        return Collections.unmodifiableMap(shared);
    }

    public void setSharedAccess(UUID target, SharedAccess access) {
        if (access == null) {
            shared.remove(target);
        } else {
            shared.put(target, access);
        }
        pruneExpired();
    }

    public void pruneExpired() {
        long now = Instant.now().toEpochMilli();
        shared.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    public boolean canView(UUID requester) {
        if (requester == null) return false;
        if (requester.equals(owner)) return true;
        SharedAccess access = shared.get(requester);
        return access != null && !access.isExpired(Instant.now().toEpochMilli());
    }

    public boolean canModify(UUID requester) {
        if (requester == null) return false;
        if (requester.equals(owner)) return true;
        SharedAccess access = shared.get(requester);
        if (access == null || access.isExpired(Instant.now().toEpochMilli())) {
            return false;
        }
        return access.getAccessLevel() == AccessLevel.MODIFY;
    }

    public int getUsedSlots() {
        return inventory.getUsedSlots();
    }

    public int getCapacity() {
        return inventory.getCapacity();
    }

    /**
     * Builds a UI page for this chest and returns the view context.
     */
    public ChestInventoryView buildView(MessageService messages, int requestedPage) {
        List<PageDefinition> pages = paginate();
        if (pages.isEmpty()) {
            pages = List.of(new PageDefinition(0, 0, 0, Math.max(9, type.getSize()), Collections.emptySet(), -1, -1, -1));
        }
        int pageIndex = Math.max(0, Math.min(requestedPage, pages.size() - 1));
        PageDefinition def = pages.get(pageIndex);
        Inventory view = Bukkit.createInventory(null, def.viewSize(), messages.color(nameWithPage(pageIndex, pages.size())));

        ItemStack[] contents = inventory.copyContents();
        int storagePtr = def.storageStart();
        for (int slot = 0; slot < def.viewSize() && storagePtr < def.storageStart() + def.storageSlots(); slot++) {
            if (def.blockedSlots().contains(slot)) {
                continue;
            }
            ItemStack stack = contents[storagePtr];
            view.setItem(slot, stack);
            storagePtr++;
        }

        ItemStack filler = filler(messages);
        for (int blocked : def.blockedSlots()) {
            view.setItem(blocked, filler);
        }
        if (def.prevSlot() >= 0) {
            view.setItem(def.prevSlot(), nav(Material.ARROW, "&ePrevious Page", messages));
        }
        if (def.infoSlot() >= 0) {
            view.setItem(def.infoSlot(), info(messages, pageIndex + 1, pages.size()));
        }
        if (def.nextSlot() >= 0) {
            view.setItem(def.nextSlot(), nav(Material.ARROW, "&eNext Page", messages));
        }

        Set<Integer> blocked = Collections.unmodifiableSet(new HashSet<>(def.blockedSlots()));
        return new ChestInventoryView(this, pageIndex, pages.size(), view, blocked, def.prevSlot(), def.infoSlot(), def.nextSlot());
    }

    /**
     * Writes any changes from the open view back into stored contents.
     */
    public void syncFromView(ChestInventoryView view) {
        if (view == null) {
            return;
        }
        List<PageDefinition> pages = paginate();
        if (pages.isEmpty()) {
            return;
        }
        PageDefinition def = pages.get(Math.max(0, Math.min(view.pageIndex(), pages.size() - 1)));
        ItemStack[] current = inventory.copyContents();
        Inventory open = view.inventory();
        int storagePtr = def.storageStart();
        for (int slot = 0; slot < def.viewSize() && storagePtr < def.storageStart() + def.storageSlots(); slot++) {
            if (def.blockedSlots().contains(slot)) {
                continue;
            }
            current[storagePtr] = open.getItem(slot);
            storagePtr++;
        }
        inventory.setContents(current);
        markModified();
    }

    public int pageCount() {
        return paginate().size();
    }

    private List<PageDefinition> paginate() {
        int capacity = inventory.getCapacity();
        if (capacity <= 0) {
            return List.of();
        }
        // Single page
        if (capacity <= 54) {
            int rows = Math.max(1, (int) Math.ceil(capacity / 9.0));
            int viewSize = rows * 9;
            int unused = viewSize - capacity;
            Set<Integer> blocked = new HashSet<>();
            for (int i = 0; i < unused; i++) {
                blocked.add(viewSize - 1 - i);
            }
            return List.of(new PageDefinition(0, 0, capacity, viewSize, blocked, -1, -1, -1));
        }

        int storagePerPage = 45; // 5 rows of storage, 1 row for navigation
        List<PageDefinition> pages = new ArrayList<>();
        int storageStart = 0;
        int pageIndex = 0;
        while (storageStart < capacity) {
            int storageSlots = Math.min(storagePerPage, capacity - storageStart);
            int storageRows = (int) Math.ceil(storageSlots / 9.0);
            int viewRows = Math.min(6, storageRows + 1); // add nav row
            int viewSize = viewRows * 9;
            int navRowStart = viewSize - 9;
            Set<Integer> blocked = new HashSet<>();
            for (int i = navRowStart; i < viewSize; i++) {
                blocked.add(i);
            }
            int totalStorageSlots = storageRows * 9;
            int unused = totalStorageSlots - storageSlots;
            for (int i = 0; i < unused; i++) {
                blocked.add(navRowStart - 1 - i);
            }
            int prevSlot = pageIndex > 0 ? navRowStart : -1;
            int infoSlot = navRowStart + 4;
            int nextSlot = -1;
            pages.add(new PageDefinition(pageIndex, storageStart, storageSlots, viewSize, blocked, prevSlot, infoSlot, nextSlot));
            storageStart += storageSlots;
            pageIndex++;
        }
        for (int i = 0; i < pages.size(); i++) {
            if (i < pages.size() - 1) {
                PageDefinition def = pages.get(i);
                pages.set(i, new PageDefinition(def.pageIndex(), def.storageStart(), def.storageSlots(), def.viewSize(), def.blockedSlots(), def.prevSlot(), def.infoSlot(), def.viewSize() - 1));
            }
        }
        return pages;
    }

    private ItemStack filler(MessageService messages) {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(messages.color("&8"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack nav(Material mat, String name, MessageService messages) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(messages.color(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack info(MessageService messages, int page, int totalPages) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(messages.color("&eStorage Info"));
        List<String> lore = new ArrayList<>();
        lore.add(messages.color("&7Size: &f" + inventory.getCapacity() + " slots"));
        lore.add(messages.color("&7Page: &f" + page + "&7/&f" + totalPages));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private String nameWithPage(int page, int totalPages) {
        if (totalPages <= 1) {
            return name;
        }
        return name + " (" + (page + 1) + "/" + totalPages + ")";
    }

    private record PageDefinition(int pageIndex, int storageStart, int storageSlots, int viewSize,
                                  Set<Integer> blockedSlots, int prevSlot, int infoSlot, int nextSlot) {
    }

    private ChestFilter defaultFilter() {
        FilterSettings settings = ChestLinkPlugin.get().upgradeSettings().getFilterSettings();
        return ChestFilter.empty(settings.getDefaultMode());
    }
}
