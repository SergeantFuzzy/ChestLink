package dev.sergeantfuzzy.chestlink.placeholder;

import dev.sergeantfuzzy.chestlink.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.InventoryType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class ChestLinkPlaceholder extends PlaceholderExpansion {
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;

    public ChestLinkPlaceholder(ChestLinkPlugin plugin, ChestLinkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "chestlink";
    }

    @Override
    public String getAuthor() {
        return "SergeantFuzzy";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        switch (params.toLowerCase()) {
            case "count":
                return String.valueOf(manager.getData(player).getChests().size());
            case "limit":
                int single = 0;
                for (int i = 1; i <= 64; i++) {
                    if (player.hasPermission("chestlink.limit.single." + i)) {
                        single = Math.max(single, i);
                    }
                }
                return String.valueOf(single);
            default:
                return "";
        }
    }
}
