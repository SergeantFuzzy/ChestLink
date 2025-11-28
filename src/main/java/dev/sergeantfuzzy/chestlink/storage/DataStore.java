package dev.sergeantfuzzy.chestlink.storage;

import dev.sergeantfuzzy.chestlink.BoundChest;
import dev.sergeantfuzzy.chestlink.InventoryType;
import dev.sergeantfuzzy.chestlink.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DataStore {
    private final File dataFolder;

    public DataStore(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public PlayerData load(UUID playerId) {
        File file = playerFile(playerId);
        PlayerData data = new PlayerData(playerId);
        if (!file.exists()) {
            return data;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("chests");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                BoundChest chest = BoundChest.from(entry, playerId);
                data.addChest(chest);
            }
        }
        return data;
    }

    public void save(PlayerData data) {
        File file = playerFile(data.getOwner());
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("chests");
        for (BoundChest chest : data.getChests()) {
            ConfigurationSection c = section.createSection(String.valueOf(chest.getId()));
            chest.serialize(c);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File playerFile(UUID playerId) {
        File folder = new File(dataFolder, "players");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new File(folder, playerId.toString() + ".yml");
    }
}
