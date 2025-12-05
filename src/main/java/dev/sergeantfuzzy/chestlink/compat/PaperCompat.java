package dev.sergeantfuzzy.chestlink.compat;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Houses Paper/Folia specific hooks behind reflection so the plugin can
 * compile against the standard Spigot API while enabling optimisations
 * when the platform supports them.
 */
public final class PaperCompat {
    private static final Logger LOGGER = Logger.getLogger(PaperCompat.class.getName());

    private PaperCompat() {
    }

    /**
     * Run a delayed task on the global region scheduler (Folia) if available.
     *
     * @return true if the task was scheduled using Paper/Folia facilities
     */
    public static boolean runGlobalTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!Platform.isPaper()) {
            return false;
        }
        try {
            Method getter = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object scheduler = getter.invoke(null);
            Method runDelayed = scheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            runDelayed.invoke(scheduler, plugin, (Consumer<Object>) ignored -> safeRun(task), delayTicks);
            return true;
        } catch (NoSuchMethodException ignored) {
            // Not Folia, fall through to Bukkit scheduler
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Global scheduler unavailable, falling back to Bukkit scheduler", ex);
        }
        return false;
    }

    /**
     * Run a delayed task bound to a specific location/region on Folia.
     *
     * @return true if the task was scheduled using Paper/Folia facilities
     */
    public static boolean runRegionTaskLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (!Platform.isPaper() || location == null || location.getWorld() == null) {
            return false;
        }
        try {
            Method getter = Bukkit.class.getMethod("getRegionScheduler");
            Object scheduler = getter.invoke(null);
            Method runDelayed = scheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            runDelayed.invoke(scheduler, plugin, location, (Consumer<Object>) ignored -> safeRun(task), delayTicks);
            return true;
        } catch (NoSuchMethodException ignored) {
            // Paper without Folia scheduler; fall through
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Region scheduler unavailable, falling back to Bukkit scheduler", ex);
        }
        return false;
    }

    /**
     * Run a delayed task on the scheduler tied to a specific entity (Folia).
     *
     * @return true if the task was scheduled using Paper/Folia facilities
     */
    public static boolean runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!Platform.isPaper() || entity == null) {
            return false;
        }
        try {
            Method getScheduler = entity.getClass().getMethod("getScheduler");
            Object scheduler = getScheduler.invoke(entity);
            Method runDelayed = scheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            runDelayed.invoke(scheduler, plugin, (Consumer<Object>) ignored -> safeRun(task), delayTicks);
            return true;
        } catch (NoSuchMethodException ignored) {
            // Paper without entity scheduler; fall through
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Entity scheduler unavailable, falling back to Bukkit scheduler", ex);
        }
        return false;
    }

    private static void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Compat task failed", t);
        }
    }
}
