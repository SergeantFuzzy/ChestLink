package dev.sergeantfuzzy.chestlink;

import dev.sergeantfuzzy.chestlink.core.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.core.storage.DataStore;
import dev.sergeantfuzzy.chestlink.features.upgrade.UpgradeRegistry;
import dev.sergeantfuzzy.chestlink.features.upgrade.UpgradeSettings;
import dev.sergeantfuzzy.chestlink.localization.MessageService;
import dev.sergeantfuzzy.chestlink.platform.command.ChestLinkCommand;
import dev.sergeantfuzzy.chestlink.platform.economy.EconomyBridge;
import dev.sergeantfuzzy.chestlink.platform.gui.FilterMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.InventoryMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.ShareMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.UpgradeMenu;
import dev.sergeantfuzzy.chestlink.platform.listener.ChestEventsListener;
import dev.sergeantfuzzy.chestlink.platform.listener.EconomyServiceListener;
import dev.sergeantfuzzy.chestlink.platform.placeholder.ChestLinkPlaceholder;
import dev.sergeantfuzzy.chestlink.core.license.LicenseService;
import dev.sergeantfuzzy.chestlink.update.PluginMetadata;
import dev.sergeantfuzzy.chestlink.update.UpdateService;
import dev.sergeantfuzzy.chestlink.update.UpdateNotificationListener;
import dev.sergeantfuzzy.chestlink.util.ConfigUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Base64;
import java.io.File;
import java.io.InputStream;

public class ChestLinkPlugin extends JavaPlugin {
    private static ChestLinkPlugin instance;
    private boolean coreStarted;
    private ChestLinkManager manager;
    private MessageService messages;
    private InventoryMenu menu;
    private ShareMenu shareMenu;
    private UpgradeRegistry upgrades;
    private UpgradeSettings upgradeSettings;
    private UpgradeMenu upgradeMenu;
    private FilterMenu filterMenu;
    private EconomyBridge economy;
    private LicenseService licenseService;
    private UpdateService updateService;
    private PluginMetadata metadata;

    public static ChestLinkPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        startCore();
    }

    @Override
    public void onDisable() {
        stopCore();
    }

    public final boolean isCoreStarted() {
        return coreStarted;
    }

    public void startCore() {
        if (coreStarted) {
            return;
        }
        instance = this;
        saveDefaultConfig();
        mergeConfigDefaults();
        File upgradesFile = new File(getDataFolder(), "upgrades.yml");
        if (!upgradesFile.exists()) {
            saveResource("upgrades.yml", false);
        }
        metadata = PluginMetadata.load(this);
        licenseService = new LicenseService(this);
        messages = new MessageService();
        DataStore store = new DataStore(getDataFolder());
        store.migrateLegacy(getDataFolder());
        upgrades = new UpgradeRegistry();
        upgrades.registerDefaults();
        upgradeSettings = UpgradeSettings.load(getDataFolder(), getLogger());
        upgrades.applySettings(upgradeSettings);
        manager = new ChestLinkManager(this, store, messages);
        menu = new InventoryMenu(this, manager, messages);
        shareMenu = new ShareMenu(this, manager, messages);
        upgradeMenu = new UpgradeMenu(this, manager, messages);
        filterMenu = new FilterMenu(this, manager, messages);
        setupEconomy(false);
        scheduleEconomyRetry();
        updateService = new UpdateService(this);

        if (licenseService != null) {
            licenseService.validateOnStartup();
        }
        ChestLinkCommand command = new ChestLinkCommand(this, manager, messages, menu, shareMenu, upgradeMenu, updateService, metadata);
        getCommand("chestlink").setExecutor(command);
        getCommand("chestlink").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(new ChestEventsListener(this, manager, messages, menu, shareMenu, upgradeMenu, filterMenu), this);
        Bukkit.getPluginManager().registerEvents(new EconomyServiceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new UpdateNotificationListener(updateService), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ChestLinkPlaceholder(this, manager).register();
        }

        if (updateService != null) {
            updateService.runStartupCheck();
        }

        coreStarted = true;
        logStatus(true);
    }

    public void stopCore() {
        if (!coreStarted) {
            return;
        }
        if (manager != null) {
            manager.saveAll();
        }
        coreStarted = false;
        logStatus(false);
    }

    public MessageService messages() {
        return messages;
    }

    public InventoryMenu menu() {
        return menu;
    }

    public ShareMenu shareMenu() {
        return shareMenu;
    }

    public UpgradeRegistry upgrades() {
        return upgrades;
    }

    public UpgradeSettings upgradeSettings() {
        return upgradeSettings;
    }

    public UpgradeMenu upgradeMenu() {
        return upgradeMenu;
    }

    public FilterMenu filterMenu() {
        return filterMenu;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public LicenseService getLicenseService() {
        return licenseService;
    }

    public UpdateService updateService() {
        return updateService;
    }

    public PluginMetadata metadata() {
        return metadata;
    }

    @Override
    public void saveConfig() {
        saveConfigPreservingComments();
    }

    public void saveConfigPreservingComments() {
        File configFile = new File(getDataFolder(), "config.yml");
        try {
            InputStream defaultStream = getResource("config.yml");
            ConfigUpdater.mergeWithValues(configFile, defaultStream, getConfig(), getLogger());
            reloadConfig();
        } catch (Exception e) {
            getLogger().warning("Failed to save config with comments: " + e.getMessage());
            super.saveConfig();
        }
    }

    public boolean isLicenseEnforced() {
        return false;
    }

    public boolean isLicenseActive() {
        return licenseService != null && licenseService.isLicensed();
    }

    public void reloadChestLink() {
        reloadConfig();
        mergeConfigDefaults();
        if (licenseService != null) {
            licenseService.reload();
            licenseService.validateOnStartup();
        }
        messages = new MessageService();
        menu = new InventoryMenu(this, manager, messages);
        shareMenu = new ShareMenu(this, manager, messages);
        upgradeSettings = UpgradeSettings.load(getDataFolder(), getLogger());
        upgrades.applySettings(upgradeSettings);
        upgradeMenu = new UpgradeMenu(this, manager, messages);
        filterMenu = new FilterMenu(this, manager, messages);
        setupEconomy(false);
        scheduleEconomyRetry();
        metadata = PluginMetadata.load(this);
        updateService = new UpdateService(this);
        HandlerList.unregisterAll(this);
        Bukkit.getPluginManager().registerEvents(new ChestEventsListener(this, manager, messages, menu, shareMenu, upgradeMenu, filterMenu), this);
        Bukkit.getPluginManager().registerEvents(new EconomyServiceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new UpdateNotificationListener(updateService), this);
        if (updateService != null) {
            updateService.runStartupCheck();
        }
    }

    public void refreshEconomy() {
        setupEconomy(false);
    }

    private void setupEconomy(boolean logMissing) {
        economy = EconomyBridge.hook(this, logMissing);
    }

    private void scheduleEconomyRetry() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (economy == null) {
                setupEconomy(true);
            }
        }, 40L);
    }

    private void logStatus(boolean enabled) {
        ConsoleCommandSender console = getServer().getConsoleSender();
        String version = getDescription().getVersion();
        String description = getDescription().getDescription() == null ? "" : getDescription().getDescription();
        String status = enabled ? "<green>ENABLED" : "<red>DISABLED";
        MiniMessage mm = MiniMessage.miniMessage();
        sendLine(console, mm, decode("K3N2ZXxIcGV2bin1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1goc="));
        sendLine(console, mm, decode("K3N2ZXxIcGV2bilMK3B4e3MpVH9yZGNbfnl8K3N2ZXxIcGV2bilKNytwZXZuKUd7YnB+eTdeeXF4ZXp2Y354eQ=="));
        sendLine(console, mm, decode("K3N2ZXxIcGV2bin1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5f1g5c="));
        sendLine(console, mm, decode("K3Bldm4pWXZ6ci03Nzc3Nzc3NytweHtzKVR/cmRjW355fCs4cHh7cyk="));
        sendLine(console, mm, decode("K3Bldm4pQXJlZH54eS03Nzc3Nytucnt7eGApK2J5c3Jle355cnMpbGFyZWR+eHlqKzhieXNyZXt+eXJzKQ==").replace("{version}", version));
        sendLine(console, mm, decode("K3Bldm4pVmJjf3hlLTc3Nzc3Nytucnt7eGApRHJlcHJ2eWNRYm1tbg=="));
        sendLine(console, mm, decode("K3Bldm4pU3JkdGV+Z2N+eHktNytgf35jcilsc3JkdGV+Z2N+eHlq").replace("{description}", description));
        sendLine(console, mm, decode("K3Bldm4pRGd+cHhjWlQtNzc3Nyt0eHt4ZS00JCIudHF0KX9jY2dkLTg4YGBgOWRnfnB4Y3p0OXhlcDhlcmR4YmV0cmQ4dH9yZGN7fnl8OSYkJyMlIDg="));
        sendLine(console, mm, decode("K3Bldm4pWnhzZX55Y38tNzc3Nyt0eHt4ZS00JCIudHF0KX9jY2dkLTg4enhzZX55Y385dHh6OGd7YnB+eTh0f3JkY3t+eXw="));
        sendLine(console, mm, decode("K3Bldm4pUH5jX2J1LTc3Nzc3Nyt0eHt4ZS00JCIudHF0KX9jY2dkLTg4cH5jf2J1OXR4ejhEcmVwcnZ5Y1FibW1uOFR/cmRjW355fDg="));
        sendLine(console, mm, decode("K3Bldm4pRGN2Y2JkLTc3Nzc3N2xkY3ZjYmRq").replace("{status}", status));
        sendLine(console, mm, decode("K3N2ZXxIcGV2bin1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1gof1goc="));
    }

    private void mergeConfigDefaults() {
        File configFile = new File(getDataFolder(), "config.yml");
        try {
            ConfigUpdater.mergeWithDefaults(configFile, getResource("config.yml"), getLogger());
            reloadConfig();
        } catch (Exception ignored) {
            // Fall back to existing config without overwriting user values.
        }
    }

    private void sendLine(ConsoleCommandSender console, MiniMessage mm, String msg) {
        Component component = mm.deserialize(msg);
        String legacy = LegacyComponentSerializer.legacySection().serialize(component);
        console.sendMessage(legacy);
    }

    private String decode(String input) {
        byte[] data = Base64.getDecoder().decode(input);
        byte key = 23;
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (data[i] ^ key);
        }
        return new String(data);
    }
}
