package dev.sergeantfuzzy.chestlink;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class ChestDropSettings {
    public enum OverflowBehavior {DROP, DENY}

    private final boolean enabled;
    private final boolean hologramEnabled;
    private final double hologramDurationSeconds;
    private final double hologramOffsetY;
    private final boolean soundEnabled;
    private final String depositSound;
    private final OverflowBehavior overflowBehavior;

    public ChestDropSettings(boolean enabled, boolean hologramEnabled, double hologramDurationSeconds, double hologramOffsetY,
                             boolean soundEnabled, String depositSound, OverflowBehavior overflowBehavior) {
        this.enabled = enabled;
        this.hologramEnabled = hologramEnabled;
        this.hologramDurationSeconds = hologramDurationSeconds;
        this.hologramOffsetY = hologramOffsetY;
        this.soundEnabled = soundEnabled;
        this.depositSound = depositSound;
        this.overflowBehavior = overflowBehavior;
    }

    public static ChestDropSettings fromConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("features.chest-drops");
        boolean enabled = section == null || section.getBoolean("enabled", true);
        ConfigurationSection hologram = section != null ? section.getConfigurationSection("hologram") : null;
        ConfigurationSection sounds = section != null ? section.getConfigurationSection("sounds") : null;
        ConfigurationSection overflow = section != null ? section.getConfigurationSection("overflow") : null;
        boolean hologramEnabled = hologram == null || hologram.getBoolean("enabled", true);
        double hologramDuration = hologram != null ? hologram.getDouble("duration", 7.0D) : 7.0D;
        double hologramOffset = hologram != null ? hologram.getDouble("offset-y", 1.2D) : 1.2D;
        boolean soundsEnabled = sounds == null || sounds.getBoolean("enabled", true);
        String depositSound = sounds != null ? sounds.getString("deposit", "ENTITY_EXPERIENCE_ORB_PICKUP") : "ENTITY_EXPERIENCE_ORB_PICKUP";
        String overflowMode = overflow != null ? overflow.getString("behavior", "drop") : "drop";
        OverflowBehavior overflowBehavior = OverflowBehavior.DROP;
        if ("deny".equalsIgnoreCase(overflowMode)) {
            overflowBehavior = OverflowBehavior.DENY;
        }
        return new ChestDropSettings(enabled, hologramEnabled, hologramDuration, hologramOffset, soundsEnabled, depositSound, overflowBehavior);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean hologramEnabled() {
        return hologramEnabled;
    }

    public double hologramDurationSeconds() {
        return hologramDurationSeconds;
    }

    public double hologramOffsetY() {
        return hologramOffsetY;
    }

    public boolean soundEnabled() {
        return soundEnabled;
    }

    public String depositSound() {
        return depositSound;
    }

    public OverflowBehavior overflowBehavior() {
        return overflowBehavior;
    }
}
