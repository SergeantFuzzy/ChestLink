package dev.sergeantfuzzy.chestlink.platform.listener;

import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.platform.economy.EconomyBridge;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;

/**
 * Listens for late-registered Vault-compatible economies and hooks into them automatically.
 */
public class EconomyServiceListener implements Listener {
    private final ChestLinkPlugin plugin;

    public EconomyServiceListener(ChestLinkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (plugin.isLicenseEnforced() && !plugin.isLicenseActive()) {
            return;
        }
        if (!EconomyBridge.isEconomyService(event.getProvider().getService())
                && !EconomyBridge.isTrustedProviderPlugin(event.getProvider().getPlugin())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, plugin::refreshEconomy);
    }
}
