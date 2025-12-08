package dev.sergeantfuzzy.chestlink.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sergeantfuzzy.chestlink.core.data.AccessLevel;
import dev.sergeantfuzzy.chestlink.core.data.BoundChest;
import dev.sergeantfuzzy.chestlink.core.data.ChestInventoryData;
import dev.sergeantfuzzy.chestlink.features.filter.ChestFilter;
import dev.sergeantfuzzy.chestlink.core.data.ChestUpgrades;
import dev.sergeantfuzzy.chestlink.core.data.InventoryType;
import dev.sergeantfuzzy.chestlink.core.data.PlayerData;
import dev.sergeantfuzzy.chestlink.core.data.SharedAccess;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.features.filter.FilterMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DataStore {
    private final File dataRoot;
    private final File playersDir;
    private final File inventoriesDir;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public DataStore(File dataFolder) {
        this.dataRoot = new File(dataFolder, "data");
        this.playersDir = new File(dataRoot, "players");
        this.inventoriesDir = new File(dataRoot, "inventories");
        ensureDirs();
    }

    private void ensureDirs() {
        if (!dataRoot.exists()) dataRoot.mkdirs();
        if (!playersDir.exists()) playersDir.mkdirs();
        if (!inventoriesDir.exists()) inventoriesDir.mkdirs();
    }

    public PlayerData load(UUID playerId) {
        ensureDirs();
        File file = playerFile(playerId);
        PlayerData data = new PlayerData(playerId);
        if (!file.exists()) {
            return data;
        }
        try {
            String raw = Files.readString(file.toPath());
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray invs = obj.has("inventories") && obj.get("inventories").isJsonArray() ? obj.getAsJsonArray("inventories") : null;
            if (invs != null) {
                for (JsonElement el : invs) {
                    String storageId = el.getAsString();
                    BoundChest chest = loadInventory(storageId);
                    if (chest != null) {
                        data.addChest(chest);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    public List<BoundChest> loadShared(UUID playerId) {
        ensureDirs();
        List<BoundChest> shared = new ArrayList<>();
        File[] files = inventoriesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return shared;
        for (File f : files) {
            try {
                String raw = Files.readString(f.toPath());
                JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                if (!obj.has("shared") || !obj.get("shared").isJsonObject()) continue;
                JsonObject sharedObj = obj.getAsJsonObject("shared");
                if (!sharedObj.has(playerId.toString())) continue;
                JsonObject entry = sharedObj.getAsJsonObject(playerId.toString());
                Long expires = entry.has("expires") && !entry.get("expires").isJsonNull() ? entry.get("expires").getAsLong() : null;
                long now = Instant.now().toEpochMilli();
                if (expires != null && expires > 0 && now >= expires) {
                    continue; // expired
                }
                BoundChest chest = loadInventory(obj);
                if (chest != null) {
                    shared.add(chest);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return shared;
    }

    public void save(PlayerData data, String playerName) {
        ensureDirs();
        List<String> storageIds = new ArrayList<>();
        for (BoundChest chest : data.getChests()) {
            storageIds.add(chest.getStorageId());
            saveInventory(chest);
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", data.getOwner().toString());
        if (playerName != null) {
            obj.addProperty("name", playerName);
        }
        JsonArray arr = new JsonArray();
        storageIds.forEach(arr::add);
        obj.add("inventories", arr);
        writeJson(playerFile(data.getOwner()), obj);
    }

    public void saveInventory(BoundChest chest) {
        ensureDirs();
        JsonObject obj = new JsonObject();
        obj.addProperty("id", String.valueOf(chest.getId()));
        obj.addProperty("storageId", chest.getStorageId());
        obj.addProperty("owner", chest.getOwner().toString());
        obj.addProperty("name", chest.getName());
        obj.addProperty("type", chest.getType().name().toLowerCase());
        obj.addProperty("created", chest.getCreatedAt());
        obj.addProperty("lastAccessed", chest.getLastAccessed());
        obj.addProperty("lastModified", chest.getLastModified());
        if (chest.getLocation() != null && chest.getLocation().getWorld() != null) {
            JsonObject loc = new JsonObject();
            loc.addProperty("world", chest.getLocation().getWorld().getName());
            loc.addProperty("x", chest.getLocation().getBlockX());
            loc.addProperty("y", chest.getLocation().getBlockY());
            loc.addProperty("z", chest.getLocation().getBlockZ());
            obj.add("location", loc);
        }
        JsonObject shared = new JsonObject();
        for (Map.Entry<UUID, SharedAccess> entry : chest.getShared().entrySet()) {
            SharedAccess access = entry.getValue();
            JsonObject s = new JsonObject();
            s.addProperty("access", access.getAccessLevel().name().toLowerCase());
            if (access.getExpiresAt() != null) {
                s.addProperty("expires", access.getExpiresAt());
            } else {
                s.add("expires", null);
            }
            shared.add(entry.getKey().toString(), s);
        }
        obj.add("shared", shared);
        JsonObject upgrades = new JsonObject();
        for (Map.Entry<String, Integer> entry : chest.getUpgrades().toSerializable().entrySet()) {
            upgrades.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("upgrades", upgrades);
        ChestFilter filter = chest.getFilter();
        if (filter != null) {
            JsonObject filterObj = new JsonObject();
            filterObj.addProperty("mode", filter.getMode().name().toLowerCase());
            JsonArray items = new JsonArray();
            filter.getEntries().forEach(material -> items.add(material.name().toLowerCase()));
            filterObj.add("items", items);
            obj.add("filter", filterObj);
        }
        obj.addProperty("contents", inventoryToBase64(chest.getInventoryData().copyContents()));
        writeJson(inventoryFile(chest.getStorageId()), obj);
    }

    public void migrateLegacy(File pluginDataFolder) {
        File legacyPlayers = new File(pluginDataFolder, "players");
        if (!legacyPlayers.exists() || !legacyPlayers.isDirectory()) {
            return;
        }
        File[] files = legacyPlayers.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return;
        for (File file : files) {
            try {
                UUID owner = UUID.fromString(file.getName().replace(".yml", ""));
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection section = config.getConfigurationSection("chests");
                PlayerData data = new PlayerData(owner);
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        ConfigurationSection entry = section.getConfigurationSection(key);
                        if (entry == null) continue;
                        BoundChest chest = BoundChest.from(entry, owner);
                        if (chest != null) {
                            data.addChest(chest);
                        }
                    }
                }
                OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
                save(data, offline.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        File archiveDir = new File(pluginDataFolder, "legacy-players-archive");
        archiveDir.mkdirs();
        for (File file : Objects.requireNonNull(legacyPlayers.listFiles())) {
            file.renameTo(new File(archiveDir, file.getName()));
        }
    }

    private BoundChest loadInventory(String storageId) {
        File file = inventoryFile(storageId);
        if (!file.exists()) return null;
        try {
            String raw = Files.readString(file.toPath());
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            return loadInventory(obj);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private BoundChest loadInventory(JsonObject obj) {
        try {
            String storageId = obj.has("storageId") ? obj.get("storageId").getAsString() : null;
            int id = Integer.parseInt(obj.has("id") ? obj.get("id").getAsString() : "0");
            UUID owner = UUID.fromString(obj.get("owner").getAsString());
            String name = obj.has("name") ? obj.get("name").getAsString() : "Chest " + id;
            InventoryType type = InventoryType.valueOf(obj.get("type").getAsString().toUpperCase());
            Location loc = null;
            if (obj.has("location") && obj.get("location").isJsonObject()) {
                JsonObject locObj = obj.getAsJsonObject("location");
                String worldName = locObj.has("world") ? locObj.get("world").getAsString() : null;
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world != null) {
                    loc = new Location(world, locObj.get("x").getAsInt(), locObj.get("y").getAsInt(), locObj.get("z").getAsInt());
                }
            }
            long created = obj.has("created") ? obj.get("created").getAsLong() : Instant.now().toEpochMilli();
            long lastAccessed = obj.has("lastAccessed") ? obj.get("lastAccessed").getAsLong() : created;
            long lastModified = obj.has("lastModified") ? obj.get("lastModified").getAsLong() : lastAccessed;
            String contents = obj.has("contents") ? obj.get("contents").getAsString() : "";
            Map<String, Integer> rawUpgrades = new HashMap<>();
            if (obj.has("upgrades") && obj.get("upgrades").isJsonObject()) {
                JsonObject upObj = obj.getAsJsonObject("upgrades");
                for (Map.Entry<String, JsonElement> entry : upObj.entrySet()) {
                    try {
                        if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                            rawUpgrades.put(entry.getKey(), entry.getValue().getAsInt());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            ChestUpgrades upgrades = ChestUpgrades.fromSerializable(rawUpgrades);
            int targetSize = type.getSize();
            ItemStackWrapper wrapper = null;
            if (!contents.isEmpty()) {
                wrapper = fromBase64(contents);
                if (wrapper != null && wrapper.contents != null) {
                    targetSize = Math.max(targetSize, wrapper.contents.length);
                }
            }
            ChestInventoryData invData = new ChestInventoryData(targetSize, wrapper != null ? wrapper.contents : null);
            ChestFilter filter = readFilter(obj);
            BoundChest chest = new BoundChest(storageId != null ? storageId : owner.toString() + "-" + id,
                    id, owner, name, type, loc, created, lastAccessed, lastModified, invData, upgrades, filter);
            if (obj.has("shared") && obj.get("shared").isJsonObject()) {
                JsonObject sharedObj = obj.getAsJsonObject("shared");
                for (Map.Entry<String, JsonElement> entry : sharedObj.entrySet()) {
                    try {
                        UUID target = UUID.fromString(entry.getKey());
                        JsonObject s = entry.getValue().getAsJsonObject();
                        AccessLevel level = AccessLevel.valueOf(s.get("access").getAsString().toUpperCase());
                        Long expires = s.has("expires") && !s.get("expires").isJsonNull() ? s.get("expires").getAsLong() : null;
                        SharedAccess access = new SharedAccess(level, expires);
                        chest.setSharedAccess(target, access);
                    } catch (Exception ignored) {
                    }
                }
                chest.pruneExpired();
            }
            return chest;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ChestFilter readFilter(JsonObject obj) {
        if (obj == null || !obj.has("filter") || !obj.get("filter").isJsonObject()) {
            return null;
        }
        JsonObject filterObj = obj.getAsJsonObject("filter");
        FilterMode mode = FilterMode.from(filterObj.has("mode") ? filterObj.get("mode").getAsString() : null,
                ChestLinkPlugin.get().upgradeSettings().getFilterSettings().getDefaultMode());
        List<String> serialized = new ArrayList<>();
        if (filterObj.has("items") && filterObj.get("items").isJsonArray()) {
            for (JsonElement el : filterObj.getAsJsonArray("items")) {
                if (el.isJsonPrimitive()) {
                    serialized.add(el.getAsString());
                }
            }
        }
        return ChestFilter.fromSerialized(mode, serialized);
    }

    private void writeJson(File target, JsonObject obj) {
        try {
            Files.writeString(target.toPath(), gson.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteInventory(String storageId) {
        File file = inventoryFile(storageId);
        if (file.exists()) {
            file.delete();
        }
    }

    private File playerFile(UUID playerId) {
        return new File(playersDir, playerId.toString() + ".json");
    }

    private File inventoryFile(String storageId) {
        return new File(inventoriesDir, storageId + ".json");
    }

    private String inventoryToBase64(org.bukkit.inventory.ItemStack[] contents) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            int size = contents != null ? contents.length : 0;
            dataOutput.writeInt(size);
            if (contents != null) {
                for (org.bukkit.inventory.ItemStack item : contents) {
                    dataOutput.writeObject(item);
                }
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private ItemStackWrapper fromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            org.bukkit.inventory.ItemStack[] items = new org.bukkit.inventory.ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (org.bukkit.inventory.ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return new ItemStackWrapper(items);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private record ItemStackWrapper(org.bukkit.inventory.ItemStack[] contents) {
    }
}