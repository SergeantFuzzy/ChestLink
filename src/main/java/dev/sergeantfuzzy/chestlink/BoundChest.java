package dev.sergeantfuzzy.chestlink;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class BoundChest {
    private final int id;
    private String name;
    private final UUID owner;
    private final InventoryType type;
    private Location location;
    private final long createdAt;
    private long lastAccessed;
    private Inventory inventory;

    public BoundChest(int id, UUID owner, String name, InventoryType type, Location location, long createdAt, long lastAccessed, Inventory inventory) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.location = location;
        this.createdAt = createdAt;
        this.lastAccessed = lastAccessed;
        this.inventory = inventory;
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

    public void markAccessed() {
        this.lastAccessed = Instant.now().toEpochMilli();
    }

    public Inventory getInventory() {
        return inventory;
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
        if (location != null) {
            section.set("world", location.getWorld() != null ? location.getWorld().getName() : null);
            section.set("x", location.getBlockX());
            section.set("y", location.getBlockY());
            section.set("z", location.getBlockZ());
        }
        section.set("created", createdAt);
        section.set("lastAccessed", lastAccessed);
        section.set("contents", inventory.getContents());
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
        return new BoundChest(id, owner, name, type, loc, created, lastAccess, inv);
    }
}
