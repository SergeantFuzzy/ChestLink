package dev.sergeantfuzzy.chestlink.localization;

import dev.sergeantfuzzy.chestlink.core.data.BoundChest;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MessageService {
    private static final String PREFIX = "&7[&6ChestLink&7]&r ";
    private final Map<String, String> messages = new HashMap<>();

    public MessageService() {
        // Core messages
        messages.put("no-permission", "&cYou do not have permission to do that.");
        messages.put("bind-require-chest", "&cYou must be holding a chest to bind.");
        messages.put("bind-start", "&aPlace your chest, then punch it to complete the bind.");
        messages.put("bind-success", "&aLinked chest &6%name% &7(#%id%).");
        messages.put("limit-reached", "&cYou have reached your chest limit (&e%limit%&c).");
        messages.put("usage-open", "&eUsage: /chestlink open <id|name>");
        messages.put("usage-rename", "&eUsage: /chestlink rename <id|oldName> <newName>");
        messages.put("usage-reset", "&eUsage: /chestlink reset <id|name>");
        messages.put("usage-delete", "&eUsage: /chestlink delete <id|name>");
        messages.put("usage-share", "&eUsage: /chestlink share <id|name> <player>");
        messages.put("usage-info", "&eUsage: /chestlink info <id|name>");
        messages.put("usage-upgrades", "&eUsage: /chestlink upgrades <id|name>");
        messages.put("usage-tp", "&eUsage: /chestlink tp <id|name>");
        messages.put("usage-admin", "&eUsage: /chestlink admin <list|open|wipe|delete|migrate|purgebroken> <player?> <id?>");
        messages.put("not-found", "&cNo linked chest found.");
        messages.put("admin-not-found", "&cPlayer not found.");
        messages.put("rename-success", "&aRenamed chest to &6%name%.");
        messages.put("rename-prompt", "&eType the new name in chat for this chest.");
        messages.put("reset", "&eReset contents for &6%name%.");
        messages.put("delete", "&cDeleted chest &6%name%.");
        messages.put("broken", "&cYour linked chest &6%name% &cwas broken and deleted.");
        messages.put("placement-unsafe", "&cYou cannot bind chests in unsafe conditions (water, lava, pistons, slime).");
        messages.put("placement-deny-mode", "&cChest linking is disabled in this game mode.");
        messages.put("tp-disabled", "&cTeleporting to chests is disabled.");
        messages.put("tp", "&aTeleported to &6%name%.");
        messages.put("reloaded", "&aChestLink config reloaded.");
        messages.put("admin-migrate", "&aChestLink data re-index completed.");
        messages.put("admin-purge", "&eRemoved %count% broken links.");
        messages.put("admin-list-header", "&eInventories for &6%player%&e:");
        messages.put("admin-open", "&aOpened chest &6%id% &afor &6%player%&a.");
        messages.put("admin-wipe", "&eWiped chest &6%id% &efor &6%player%&e.");
        messages.put("admin-delete", "&cDeleted chest &6%id% &cfor &6%player%&c.");
        messages.put("admin-missing-id", "&cYou must specify a chest id or name.");
        messages.put("shared", "&aShared chest with &6%player% &aas &e%access%&a.");
        messages.put("upgrade-disabled", "&cThat upgrade is disabled.");
        messages.put("upgrade-owner-only", "&cOnly the owner can upgrade this chest.");
        messages.put("upgrade-max-level", "&cThis upgrade is already at max level.");
        messages.put("upgrade-no-economy", "&cEconomy support is unavailable for upgrade costs.");
        messages.put("upgrade-no-funds", "&cYou need &6%amount% &cto purchase this upgrade.");
        messages.put("upgrade-no-xp", "&cYou need &6%levels% &cxp levels for this upgrade.");
        messages.put("upgrade-no-items", "&cMissing required items: &6%items%&c.");
        messages.put("upgrade-success", "&aUpgraded &6%name% &ato level &e%level%&a.");
        messages.put("no-linked-chests", "&cYou have no linked chests yet. &7Hover for setup steps.");
        messages.put("filter-mode", "&aFilter mode set to &6%mode%.");
        messages.put("filter-add", "&aAdded &6%item% &ato filter.");
        messages.put("filter-add-none", "&cHold an item to add it to the filter.");
        messages.put("filter-add-failed", "&cCannot add &6%item%&c (duplicate or limit reached).");
        messages.put("filter-remove", "&eRemoved &6%item% &efrom filter.");
        messages.put("filter-cleared", "&eCleared all filter entries.");
        messages.put("filter-blocked", "&cThat item cannot enter this chest.");
        messages.put("filter-returned", "&eDisallowed items were returned to you.");
        messages.put("filter-dropped", "&eDisallowed items were dropped nearby.");
        messages.put("compression-returned", "&eCompressed blocks were returned to you due to lack of space.");
        messages.put("compression-dropped", "&eCompressed blocks were dropped nearby due to lack of space.");
        messages.put("compression-blocked", "&cCompression failed because there is no room for new blocks.");
        // Upgrade localization
        messages.put("upgrades.capacity.name", "&6Capacity Upgrade");
        messages.put("upgrades.capacity.description", "&7Expand virtual storage beyond the base inventory.");
        messages.put("upgrades.auto_sort.name", "&6Auto-Sort Upgrade");
        messages.put("upgrades.auto_sort.description", "&7Automatically sorts the chest contents after new items arrive.");
        messages.put("upgrades.filter.name", "&6Auto-Filter Upgrade");
        messages.put("upgrades.filter.description", "&7Allow or deny items using whitelist/blacklist rules.");
        messages.put("upgrades.compression.name", "&6Compression Upgrade");
        messages.put("upgrades.compression.description", "&7Automatically crafts compressible items into their block forms.");
        messages.put("upgrade-no-permission-upgrade", "&cYou do not have permission to unlock %upgrade%.");
        messages.put("upgrade-limit-level", "&cYou cannot upgrade %upgrade% beyond level %limit%.");
        messages.put("upgrade-limit-count", "&cYou have reached the limit of %limit% unlocked %upgrade% chests.");
    }

    public String getPrefix() {
        return color(PREFIX);
    }

    public String msg(String path, Map<String, String> placeholders) {
        String raw = messages.getOrDefault(path, path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return color(getPrefix() + raw);
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(msg(path, placeholders));
    }

    public void sendWithHover(CommandSender sender, String path, Map<String, String> placeholders, List<String> hoverLore) {
        if (!(sender instanceof Player player)) {
            send(sender, path, placeholders);
            return;
        }
        String base = msg(path, placeholders);
        TextComponent component = new TextComponent(TextComponent.fromLegacyText(base));
        if (hoverLore != null && !hoverLore.isEmpty()) {
            List<BaseComponent> hoverComponents = new java.util.ArrayList<>();
            for (int i = 0; i < hoverLore.size(); i++) {
                String rawLine = hoverLore.get(i);
                boolean isStrike = rawLine.contains("&m") || rawLine.contains(ChatColor.STRIKETHROUGH.toString());
                BaseComponent[] parts = TextComponent.fromLegacyText(ChatColor.RESET + color(rawLine) + ChatColor.RESET);
                if (!isStrike) {
                    for (BaseComponent part : parts) {
                        part.setStrikethrough(false);
                    }
                }
                for (BaseComponent part : parts) {
                    hoverComponents.add(part);
                }
                if (i < hoverLore.size() - 1) {
                    TextComponent newline = new TextComponent("\n");
                    newline.setStrikethrough(false);
                    hoverComponents.add(newline);
                }
            }
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    hoverComponents.toArray(new BaseComponent[0])));
        }
        player.spigot().sendMessage(component);
    }

    public String text(String path, Map<String, String> placeholders) {
        String raw = messages.getOrDefault(path, path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return color(raw);
    }

    public String text(String path) {
        return text(path, null);
    }

    public boolean has(String path) {
        return messages.containsKey(path);
    }

    public void sendClickableRename(Player player, BoundChest chest, List<String> hoverLore) {
        if (player == null || chest == null) {
            return;
        }
        String base = color(getPrefix() + "&aRenamed chest to ");
        TextComponent main = new TextComponent(TextComponent.fromLegacyText(base));
        TextComponent name = new TextComponent(color("&6" + chest.getName()));
        name.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chestlink open " + chest.getId()));
        if (hoverLore != null && !hoverLore.isEmpty()) {
            String hover = String.join("\n", hoverLore);
            name.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hover)));
        }
        main.addExtra(name);
        main.addExtra(new TextComponent(color("&a.")));
        player.spigot().sendMessage(main);
    }

    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
