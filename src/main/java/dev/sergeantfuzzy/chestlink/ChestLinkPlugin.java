package dev.sergeantfuzzy.chestlink;

import dev.sergeantfuzzy.chestlink.command.ChestLinkCommand;
import dev.sergeantfuzzy.chestlink.gui.InventoryMenu;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import dev.sergeantfuzzy.chestlink.listener.ChestEventsListener;
import dev.sergeantfuzzy.chestlink.placeholder.ChestLinkPlaceholder;
import dev.sergeantfuzzy.chestlink.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ChestLinkPlugin extends JavaPlugin {
    private static ChestLinkPlugin instance;
    private ChestLinkManager manager;
    private MessageService messages;
    private InventoryMenu menu;

    public static ChestLinkPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        messages = new MessageService();
        manager = new ChestLinkManager(new DataStore(getDataFolder()), messages);
        menu = new InventoryMenu(this, manager, messages);

        ChestLinkCommand command = new ChestLinkCommand(this, manager, messages, menu);
        getCommand("chestlink").setExecutor(command);
        getCommand("chestlink").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(new ChestEventsListener(this, manager, messages, menu), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ChestLinkPlaceholder(this, manager).register();
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveAll();
        }
    }

    public MessageService messages() {
        return messages;
    }

    public InventoryMenu menu() {
        return menu;
    }

    public void reloadChestLink() {
        reloadConfig();
        messages = new MessageService();
        menu = new InventoryMenu(this, manager, messages);
    }
}
