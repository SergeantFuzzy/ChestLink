package dev.sergeantfuzzy.chestlink.platform.gui;

import dev.sergeantfuzzy.chestlink.core.data.AccessLevel;
import dev.sergeantfuzzy.chestlink.core.data.BoundChest;
import dev.sergeantfuzzy.chestlink.core.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.core.data.SharedAccess;
import dev.sergeantfuzzy.chestlink.localization.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class ShareMenu {
    private static final String TITLE = "&8Share Access";
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private final MessageService messages;
    private final Map<java.util.UUID, ShareContext> contexts = new HashMap<>();

    public ShareMenu(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public void open(Player player, BoundChest chest, java.util.UUID target, String targetName) {
        Inventory inv = Bukkit.createInventory(player, 9, messages.color(TITLE));
        inv.setItem(3, option(Material.PAPER, "&eView Only", targetName, AccessLevel.VIEW));
        inv.setItem(5, option(Material.WRITABLE_BOOK, "&aModify", targetName, AccessLevel.MODIFY));
        contexts.put(player.getUniqueId(), new ShareContext(chest, target, targetName, player.getName()));
        player.openInventory(inv);
    }

    private ItemStack option(Material material, String name, String targetName, AccessLevel access) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color(name));
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(messages.color("&7Grant &f" + targetName + " &7access."));
        lore.add(messages.color("&7Level: &f" + access.name().toLowerCase()));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isShareMenu(String title) {
        return org.bukkit.ChatColor.stripColor(title).equalsIgnoreCase(org.bukkit.ChatColor.stripColor(messages.color(TITLE)));
    }

    public void handleClick(Player player, int slot) {
        ShareContext context = contexts.get(player.getUniqueId());
        if (context == null) return;
        if (slot != 3 && slot != 5) return;
        AccessLevel level = slot == 3 ? AccessLevel.VIEW : AccessLevel.MODIFY;
        long expirySeconds = plugin.getConfig().getLong("share-default-expiry-seconds", 0L);
        Long expires = expirySeconds > 0 ? System.currentTimeMillis() + (expirySeconds * 1000L) : null;
        manager.shareChest(context.chest(), context.target(), level, expires);
        messages.send(player, "shared", Map.of("player", context.targetName(), "access", level.name().toLowerCase()));
        notifyTarget(context, level);
        contexts.remove(player.getUniqueId());
        player.closeInventory();
    }

    public void clear(Player player) {
        contexts.remove(player.getUniqueId());
    }

    private void notifyTarget(ShareContext context, AccessLevel level) {
        Player targetPlayer = Bukkit.getPlayer(context.target());
        if (targetPlayer == null) {
            return;
        }
        String access = level.name().toLowerCase();
        TextComponent msg = new TextComponent(ChatColor.GRAY + "You were granted " + ChatColor.GOLD + access
                + ChatColor.GRAY + " access to " + ChatColor.YELLOW + context.chest().getName()
                + ChatColor.GRAY + " by " + ChatColor.AQUA + context.ownerName() + ChatColor.GRAY + ". ");
        TextComponent open = new TextComponent(ChatColor.GREEN + "[Open]");
        open.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/chestlink open " + context.chest().getId()));
        msg.addExtra(open);
        targetPlayer.spigot().sendMessage(msg);
    }

    private record ShareContext(BoundChest chest, java.util.UUID target, String targetName, String ownerName) {
    }
}