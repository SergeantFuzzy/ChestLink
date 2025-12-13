package dev.sergeantfuzzy.chestlink.platform.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralised scheduler utility that chooses the safest available scheduler for the
 * current platform (Spigot, Paper, Folia).
 */
public final class SchedulerCompat {
    private SchedulerCompat() {
    }

    public static void runLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        if (PaperCompat.runGlobalTaskLater(plugin, task, delayTicks)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void runLocationTaskLater(JavaPlugin plugin, Location location, Runnable task, long delayTicks) {
        if (PaperCompat.runRegionTaskLater(plugin, location, task, delayTicks)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void runEntityTaskLater(JavaPlugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (PaperCompat.runEntityTaskLater(plugin, entity, task, delayTicks)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}