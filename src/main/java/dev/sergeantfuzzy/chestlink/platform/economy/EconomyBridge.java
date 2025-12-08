package dev.sergeantfuzzy.chestlink.platform.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflection based hook that lets ChestLink talk to Vault-compatible economies without
 * requiring the Vault API to be present at class load time.
 */
public final class EconomyBridge {
    private static final String ECONOMY_CLASS_NAME = "net.milkbowl.vault.economy.Economy";
    private static final Set<String> TRUSTED_PROVIDER_NAMES = Set.of(
            "vault",
            "vaultunlocked",
            "essentials",
            "essentialsx",
            "thenewconomy",
            "tne"
    );

    private final Logger logger;
    private final Object provider;
    private final Method hasPlayerMethod;
    private final Method withdrawPlayerMethod;
    private final Method balanceMethod;
    private final Method transactionSuccessMethod;
    private final boolean withdrawReturnsBoolean;
    private final String providerName;

    private EconomyBridge(Logger logger,
                          Object provider,
                          Method hasPlayerMethod,
                          Method withdrawPlayerMethod,
                          Method balanceMethod,
                          Method transactionSuccessMethod,
                          boolean withdrawReturnsBoolean,
                          String providerName) {
        this.logger = logger;
        this.provider = provider;
        this.hasPlayerMethod = hasPlayerMethod;
        this.withdrawPlayerMethod = withdrawPlayerMethod;
        this.balanceMethod = balanceMethod;
        this.transactionSuccessMethod = transactionSuccessMethod;
        this.withdrawReturnsBoolean = withdrawReturnsBoolean;
        this.providerName = providerName;
    }

    public static EconomyBridge hook(JavaPlugin plugin, boolean logMissing) {
        Logger logger = plugin.getLogger();
        ServicesManager services = plugin.getServer().getServicesManager();
        Collection<RegisteredServiceProvider<?>> registrations = findEconomyRegistrations(plugin, services);
        if (registrations.isEmpty()) {
            if (logMissing) {
                logger.warning("[ChestLink] Vault-compatible economy API not found. Economy-based upgrade costs disabled.");
            }
            return null;
        }
        for (RegisteredServiceProvider<?> registration : registrations) {
            Object provider = registration.getProvider();
            if (provider == null) {
                continue;
            }
            try {
                Method hasPlayer = findPlayerMethod(provider.getClass(), "has");
                Method withdrawPlayer = findPlayerMethod(provider.getClass(), "withdrawPlayer");
                Method balance = findBalanceMethod(provider.getClass());
                Class<?> responseType = withdrawPlayer.getReturnType();
                boolean returnsBoolean = responseType == boolean.class || responseType == Boolean.class;
                Method transactionSuccess = null;
                if (!returnsBoolean) {
                    transactionSuccess = responseType.getMethod("transactionSuccess");
                }
                String hooked = registration.getPlugin() != null
                        ? registration.getPlugin().getName()
                        : provider.getClass().getSimpleName();
                logger.info("[ChestLink] Hooked into economy provider: " + hooked);
                return new EconomyBridge(logger, provider, hasPlayer, withdrawPlayer, balance, transactionSuccess, returnsBoolean, hooked);
            } catch (ReflectiveOperationException ex) {
                logger.log(Level.WARNING, "[ChestLink] Failed to initialize economy bridge for provider "
                        + provider.getClass().getName() + ".", ex);
            }
        }
        if (logMissing) {
            logger.warning("[ChestLink] Vault-compatible economy API not found. Economy-based upgrade costs disabled.");
        }
        return null;
    }

    private static Collection<RegisteredServiceProvider<?>> findEconomyRegistrations(JavaPlugin plugin, ServicesManager services) {
        Collection<RegisteredServiceProvider<?>> results = new ArrayList<>();
        Collection<Class<?>> known = services.getKnownServices();
        for (Class<?> service : known) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = services.getRegistration((Class) service);
            if (registration != null && (isEconomyService(service) || isTrustedProvider(registration))) {
                results.add(registration);
            }
        }
        Plugin[] plugins = plugin.getServer().getPluginManager().getPlugins();
        for (Plugin other : plugins) {
            for (RegisteredServiceProvider<?> registration : services.getRegistrations(other)) {
                if (isEconomyService(registration.getService()) || isTrustedProvider(registration)) {
                    results.add(registration);
                }
            }
        }
        return results;
    }

    private static Method findPlayerMethod(Class<?> economyClass, String name) throws NoSuchMethodException {
        try {
            return economyClass.getMethod(name, OfflinePlayer.class, double.class);
        } catch (NoSuchMethodException ignored) {
        }
        return economyClass.getMethod(name, Player.class, double.class);
    }

    private static Method findBalanceMethod(Class<?> economyClass) {
        try {
            return economyClass.getMethod("getBalance", OfflinePlayer.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return economyClass.getMethod("getBalance", Player.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return economyClass.getMethod("getBalance", String.class);
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    public static boolean isEconomyService(Class<?> service) {
        return service != null && ECONOMY_CLASS_NAME.equals(service.getName());
    }

    private static boolean isTrustedProvider(RegisteredServiceProvider<?> registration) {
        return registration != null && isTrustedProviderPlugin(registration.getPlugin());
    }

    public boolean has(Player player, double amount) {
        if (player == null) {
            return false;
        }
        try {
            Object result = hasPlayerMethod.invoke(provider, player, amount);
            return result instanceof Boolean && (Boolean) result;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            logger.log(Level.WARNING, "[ChestLink] Failed to query economy balance for " + player.getName(), ex);
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (player == null) {
            return false;
        }
        try {
            Object response = withdrawPlayerMethod.invoke(provider, player, amount);
            if (withdrawReturnsBoolean) {
                return response instanceof Boolean && (Boolean) response;
            }
            if (response == null || transactionSuccessMethod == null) {
                return false;
            }
            Object success = transactionSuccessMethod.invoke(response);
            return success instanceof Boolean && (Boolean) success;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            logger.log(Level.WARNING, "[ChestLink] Failed to withdraw funds for " + player.getName(), ex);
            return false;
        }
    }

    public double balance(Player player) {
        if (player == null || balanceMethod == null) {
            return 0D;
        }
        try {
            Class<?> param = balanceMethod.getParameterTypes()[0];
            Object arg = param == String.class ? player.getName() : player;
            Object result = balanceMethod.invoke(provider, arg);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            logger.log(Level.WARNING, "[ChestLink] Failed to query economy balance for " + player.getName(), ex);
        }
        return 0D;
    }

    public String getProviderName() {
        return providerName;
    }

    public static boolean isTrustedProviderPlugin(Plugin plugin) {
        if (plugin == null) {
            return false;
        }
        String name = plugin.getName();
        if (name == null) {
            return false;
        }
        return TRUSTED_PROVIDER_NAMES.contains(name.toLowerCase(Locale.ENGLISH));
    }
}
