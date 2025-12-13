package dev.sergeantfuzzy.chestlink.compat;

import org.bukkit.Bukkit;

/**
 * Simple runtime platform detection that is safe to call on all targets.
 */
public final class Platform {
    private static final boolean PAPER = detectPaper();
    private static final boolean FOLIA = detectFolia();

    private Platform() {
    }

    public static boolean isPaper() {
        return PAPER;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("io.papermc.paper.configuration.Configuration");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            String name = Bukkit.getServer().getName();
            return name != null && name.toLowerCase().contains("paper");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
