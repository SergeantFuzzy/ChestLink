package dev.sergeantfuzzy.chestlink.lang;

import dev.sergeantfuzzy.chestlink.BoundChest;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
