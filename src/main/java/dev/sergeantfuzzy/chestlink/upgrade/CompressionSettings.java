package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.FilterOverflowBehavior;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class CompressionSettings {
    private final Map<Material, Material> recipes;
    private final FilterOverflowBehavior overflowBehavior;

    public CompressionSettings(Map<Material, Material> recipes, FilterOverflowBehavior overflowBehavior) {
        this.recipes = recipes == null ? Collections.emptyMap() : Collections.unmodifiableMap(recipes);
        this.overflowBehavior = overflowBehavior == null ? FilterOverflowBehavior.RETURN : overflowBehavior;
    }

    public static CompressionSettings defaults() {
        return new CompressionSettings(Collections.emptyMap(), FilterOverflowBehavior.RETURN);
    }

    public Map<Material, Material> recipes() {
        return recipes;
    }

    public FilterOverflowBehavior overflowBehavior() {
        return overflowBehavior;
    }

    public static CompressionSettings fromSection(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return defaults();
        }
        Map<Material, Material> recipes = new LinkedHashMap<>();
        ConfigurationSection recipeSection = section.getConfigurationSection("recipes");
        if (recipeSection != null) {
            for (String key : recipeSection.getKeys(false)) {
                Material input = Material.matchMaterial(key.toUpperCase(Locale.ENGLISH));
                if (input == null) {
                    logger.warning("[ChestLink] Unknown compression input '" + key + "'.");
                    continue;
                }
                Material output = Material.matchMaterial(recipeSection.getString(key, "").toUpperCase(Locale.ENGLISH));
                if (output == null) {
                    logger.warning("[ChestLink] Unknown compression output for '" + key + "'.");
                    continue;
                }
                recipes.put(input, output);
            }
        }
        FilterOverflowBehavior overflow = FilterOverflowBehavior.from(section.getString("overflow-behavior"), FilterOverflowBehavior.RETURN);
        return new CompressionSettings(recipes, overflow);
    }
}
