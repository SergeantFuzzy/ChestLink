package dev.sergeantfuzzy.chestlink;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
    private Inventory inventory;
    private final ChestUpgrades upgrades;
    private final Map<UUID, SharedAccess> shared = new HashMap<>();

    public BoundChest(String storageId, int id, UUID owner, String name, InventoryType type, Location location, long createdAt, long lastAccessed, long lastModified, Inventory inventory) {
        this(storageId, id, owner, name, type, location, createdAt, lastAccessed, lastModified, inventory, new ChestUpgrades());
    }

    public BoundChest(String storageId, int id, UUID owner, String name, InventoryType type, Location location, long createdAt, long lastAccessed, long lastModified, Inventory inventory, ChestUpgrades upgrades) {
        this.storageId = storageId;
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.location = location;
        this.createdAt = createdAt;
        this.lastAccessed = lastAccessed;
        this.lastModified = lastModified;
        this.inventory = inventory;
        this.upgrades = upgrades != null ? upgrades : new ChestUpgrades();
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

    public Inventory getInventory() {
        return inventory;
    }

    public ChestUpgrades getUpgrades() {
        return upgrades;
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
        return Objects.equals(location.getWorld(), other.getWorld())
                && location.getBlockX() == other.getBlockX()
                && location.getBlockY() == other.getBlockY()
                && location.getBlockZ() == other.getBlockZ();
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
        section.set("contents", inventory.getContents());
        Map<String, Integer> upgradeMap = upgrades.toSerializable();
        if (!upgradeMap.isEmpty()) {
            ConfigurationSection upgradesSection = section.createSection("upgrades");
            for (Map.Entry<String, Integer> entry : upgradeMap.entrySet()) {
                upgradesSection.set(entry.getKey(), entry.getValue());
            }
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
        Inventory inv = Bukkit.createInventory(null, type.getSize(), name);
        ItemStack[] contentArray = contents.toArray(new ItemStack[contents.size()]);
        inv.setContents(contentArray);
        String storageId = owner.toString() + "-" + id;
        Map<String, Integer> upgradesRaw = new HashMap<>();
        ConfigurationSection upgradesSection = section.getConfigurationSection("upgrades");
        if (upgradesSection != null) {
            for (String key : upgradesSection.getKeys(false)) {
                upgradesRaw.put(key, upgradesSection.getInt(key));
            }
        }
        ChestUpgrades upgrades = ChestUpgrades.fromSerializable(upgradesRaw);
        BoundChest bound = new BoundChest(storageId, id, owner, name, type, loc, created, lastAccess, lastModified, inv, upgrades);
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
        int used = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() != org.bukkit.Material.AIR) {
                used++;
            }
        }
        return used;
    }
}
