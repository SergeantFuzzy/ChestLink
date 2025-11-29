package dev.sergeantfuzzy.chestlink;

import dev.sergeantfuzzy.chestlink.command.ChestLinkCommand;
import dev.sergeantfuzzy.chestlink.gui.InventoryMenu;
import dev.sergeantfuzzy.chestlink.gui.ShareMenu;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import dev.sergeantfuzzy.chestlink.listener.ChestEventsListener;
import dev.sergeantfuzzy.chestlink.placeholder.ChestLinkPlaceholder;
import dev.sergeantfuzzy.chestlink.storage.DataStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.Base64;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class ChestLinkPlugin extends JavaPlugin {
    private static ChestLinkPlugin instance;
    private ChestLinkManager manager;
    private MessageService messages;
    private InventoryMenu menu;
    private ShareMenu shareMenu;

    public static ChestLinkPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        messages = new MessageService();
        DataStore store = new DataStore(getDataFolder());
        store.migrateLegacy(getDataFolder());
        manager = new ChestLinkManager(store, messages);
        menu = new InventoryMenu(this, manager, messages);
        shareMenu = new ShareMenu(this, manager, messages);

        ChestLinkCommand command = new ChestLinkCommand(this, manager, messages, menu, shareMenu);
        getCommand("chestlink").setExecutor(command);
        getCommand("chestlink").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(new ChestEventsListener(this, manager, messages, menu, shareMenu), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ChestLinkPlaceholder(this, manager).register();
        }

        logStatus(true);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveAll();
        }
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

    public void reloadChestLink() {
        reloadConfig();
        messages = new MessageService();
        menu = new InventoryMenu(this, manager, messages);
        shareMenu = new ShareMenu(this, manager, messages);
        HandlerList.unregisterAll(this);
        Bukkit.getPluginManager().registerEvents(new ChestEventsListener(this, manager, messages, menu, shareMenu), this);
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
