package dev.sergeantfuzzy.chestlink.update;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.Properties;

public class PluginMetadata {
    private final String releaseDate;
    private final String description;
    private final String name;

    private PluginMetadata(String name, String description, String releaseDate) {
        this.name = name;
        this.description = description;
        this.releaseDate = releaseDate;
    }

    public static PluginMetadata load(JavaPlugin plugin) {
        Properties props = new Properties();
        try (InputStream in = plugin.getResource("plugin-metadata.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
            // fall back to plugin.yml values
        }
        String name = props.getProperty("name", plugin.getDescription().getName());
        String desc = props.getProperty("description", plugin.getDescription().getDescription());
        String release = props.getProperty("release-date", "Unknown");
        return new PluginMetadata(name, desc, release);
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }
}
