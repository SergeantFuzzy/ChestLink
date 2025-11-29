package dev.sergeantfuzzy.chestlink.command;

import dev.sergeantfuzzy.chestlink.BoundChest;
import dev.sergeantfuzzy.chestlink.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.InventoryType;
import dev.sergeantfuzzy.chestlink.PlayerData;
import dev.sergeantfuzzy.chestlink.gui.InventoryMenu;
import dev.sergeantfuzzy.chestlink.gui.ShareMenu;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

public class ChestLinkCommand implements CommandExecutor, TabCompleter {
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private MessageService messages;
    private InventoryMenu menu;
    private ShareMenu shareMenu;

    public ChestLinkCommand(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages, InventoryMenu menu, ShareMenu shareMenu) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.menu = menu;
        this.shareMenu = shareMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("chestlink.use")) {
            messages.send(player, "no-permission", null);
            return true;
        }
        if (args.length == 0) {
            if (!player.hasPermission("chestlink.menu")) {
                messages.send(player, "no-permission", null);
                return true;
            }
            menu.open(player, 0);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ENGLISH);
        switch (sub) {
            case "bind":
                handleBind(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "open":
                handleOpen(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "rename":
                handleRename(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "reset":
                handleReset(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "delete":
                handleDelete(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "share":
                handleShare(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "info":
                handleInfo(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "limits":
                handleLimits(player);
                return true;
            case "help":
                sendHelp(player);
                return true;
            case "tp":
                handleTeleport(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "reload":
                handleReload(player);
                return true;
            case "admin":
                handleAdmin(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            default:
                sendHelp(player);
                return true;
    }
}

    private void handleBind(Player player, String[] args) {
        if (!player.hasPermission("chestlink.bind")) {
            messages.send(player, "no-permission", null);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || (hand.getType() != Material.CHEST && hand.getType() != Material.TRAPPED_CHEST)) {
            messages.send(player, "bind-require-chest", null);
            return;
        }
        String name = args.length > 0 ? String.join(" ", args) : null;
        InventoryType type = player.isSneaking() && hasDoubleAccess(player) ? InventoryType.DOUBLE : InventoryType.SINGLE;
        int limit = getLimit(player, type);
        if (!manager.canCreate(player, type, limit)) {
            messages.send(player, "limit-reached", Map.of("limit", String.valueOf(limit)));
            return;
        }
        manager.startBind(player, name, type);
        messages.send(player, "bind-start", Map.of("name", name == null ? "New chest" : name));
    }

    private void handleOpen(Player player, String[] args) {
        if (!player.hasPermission("chestlink.open")) {
            messages.send(player, "no-permission", null);
            return;
        }
        if (args.length < 1) {
            messages.send(player, "usage-open", null);
            return;
        }
        BoundChest chest = manager.getAccessibleChest(player, args[0]);
        if (chest == null) {
            messages.send(player, "not-found", null);
            return;
        }
        if (!manager.canView(player, chest)) {
            messages.send(player, "no-permission", null);
            return;
        }
        manager.sortInventory(chest.getInventory());
        chest.markAccessed();
        manager.saveInventory(chest);
        player.openInventory(chest.getInventory());
    }

    private void handleRename(Player player, String[] args) {
        if (!player.hasPermission("chestlink.rename")) {
            messages.send(player, "no-permission", null);
            return;
        }
        if (args.length < 2) {
            messages.send(player, "usage-rename", null);
            return;
        }
        BoundChest chest = manager.getOwnedChest(player, args[0]);
        if (chest == null) {
            messages.send(player, "not-found", null);
            return;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        chest.setName(newName);
        chest.markModified();
        messages.send(player, "rename-success", Map.of("name", newName));
        manager.save(player);
    }

    private void handleReset(Player player, String[] args) {
        if (!player.hasPermission("chestlink.reset")) {
            messages.send(player, "no-permission", null);
            return;
        }
        if (args.length < 1) {
            messages.send(player, "usage-reset", null);
            return;
        }
        BoundChest chest = manager.getOwnedChest(player, args[0]);
        if (chest == null) {
            messages.send(player, "not-found", null);
            return;
        }
        manager.resetChest(chest);
        messages.send(player, "reset", Map.of("name", chest.getName()));
        manager.save(player);
    }

    private void handleDelete(Player player, String[] args) {
        if (!player.hasPermission("chestlink.delete")) {
            messages.send(player, "no-permission", null);
            return;
        }
        if (args.length < 1) {
            messages.send(player, "usage-delete", null);
            return;
        }
        BoundChest chest = manager.getOwnedChest(player, args[0]);
        if (chest == null) {
            messages.send(player, "not-found", null);
            return;
        }
        manager.deleteChest(player, chest);
        messages.send(player, "delete", Map.of("name", chest.getName()));
        manager.save(player);
    }

    private void handleShare(Player player, String[] args) {
        if (!player.hasPermission("chestlink.share")) {
            messages.send(player, "no-permission", null);
            return;
        }
        if (args.length < 2) {
            messages.send(player, "usage-share", null);
            return;
        }
        BoundChest chest = manager.getOwnedChest(player, args[0]);
        if (chest == null) {
            messages.send(player, "not-found", null);
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        UUID targetId;
        String targetName;
        if (target != null) {
            targetId = target.getUniqueId();
            targetName = target.getName();
        } else {
            try {
                targetId = UUID.fromString(args[1]);
                targetName = args[1];
            } catch (IllegalArgumentException e) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                if (offline == null || offline.getUniqueId() == null) {
                    messages.send(player, "admin-not-found", null);
                    return;
                }
                targetId = offline.getUniqueId();
                targetName = offline.getName() == null ? args[1] : offline.getName();
            }
        }
        if (player.getUniqueId().equals(targetId)) {
            messages.send(player, "no-permission", null);
            return;
        }
        shareMenu.open(player, chest, targetId, targetName);
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 1) {
            messages.send(player, "usage-info", null);
            return;
        }
        BoundChest chest = manager.getAccessibleChest(player, args[0]);
        if (chest == null) {
            messages.send(player, "not-found", null);
            return;
        }
        Location loc = chest.getLocation();
        String locText = loc == null ? "Unknown" : (loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")");
        player.sendMessage(messages.color(messages.getPrefix() + "&eChest #" + chest.getId() + " &7- &6" + chest.getName()));
        player.sendMessage(messages.color("&7Type: &6" + chest.getType().getDisplayName() + " &7Slots: &6" + chest.getType().getSize()));
        player.sendMessage(messages.color("&7Created: &6" + new Date(chest.getCreatedAt())));
        player.sendMessage(messages.color("&7Location: &6" + locText));
    }

    private void handleLimits(Player player) {
        PlayerData data = manager.getData(player);
        boolean isOp = player.isOp();
        int singleLimit = getLimit(player, InventoryType.SINGLE);
        int doubleLimit = getLimit(player, InventoryType.DOUBLE);
        String singleLimitText = isOp ? "\u221e" : String.valueOf(singleLimit);
        String doubleLimitText = isOp ? "\u221e" : String.valueOf(doubleLimit);
        player.sendMessage(messages.color(messages.getPrefix() + "&eLimits"));
        player.sendMessage(messages.color("&7Single: &6" + data.countByType(InventoryType.SINGLE) + "&7/&6" + singleLimitText));
        player.sendMessage(messages.color("&7Double: &6" + data.countByType(InventoryType.DOUBLE) + "&7/&6" + doubleLimitText));
    }

    private void handleTeleport(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("teleport-enabled", false)) {
            messages.send(player, "tp-disabled", null);
            return;
        }
        if (!player.hasPermission("chestlink.tp")) {
            messages.send(player, "no-permission", null);
            return;
        }
        if (args.length < 1) {
            messages.send(player, "usage-tp", null);
            return;
        }
        BoundChest chest = manager.getAccessibleChest(player, args[0]);
        if (chest == null || chest.getLocation() == null || !manager.canView(player, chest)) {
            messages.send(player, "not-found", null);
            return;
        }
        player.teleport(chest.getLocation().add(0.5, 1, 0.5));
        messages.send(player, "tp", Map.of("name", chest.getName()));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("chestlink.admin.reload")) {
            messages.send(player, "no-permission", null);
            return;
        }
        plugin.reloadChestLink();
        this.messages = plugin.messages();
        this.menu = plugin.menu();
        this.shareMenu = plugin.shareMenu();
        sendReloadButton(player);
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("chestlink.admin")) {
            messages.send(player, "no-permission", null);
            return;
        }
        if (args.length < 1) {
            messages.send(player, "usage-admin", null);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ENGLISH);
        if (sub.equals("list") && args.length >= 2) {
            if (!player.hasPermission("chestlink.admin.view")) {
                messages.send(player, "no-permission", null);
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messages.send(player, "admin-not-found", null);
                return;
            }
            sendAdminList(player, target);
        } else if (sub.equals("open")) {
            if (!player.hasPermission("chestlink.admin.open")) {
                messages.send(player, "no-permission", null);
                return;
            }
            if (args.length < 3) {
                messages.send(player, "admin-missing-id", null);
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messages.send(player, "admin-not-found", null);
                return;
            }
            boolean readOnly = args.length >= 4 && args[3].equalsIgnoreCase("readonly");
            BoundChest chest = manager.getOwnedChest(target, args[2]);
            if (chest == null) {
                messages.send(player, "not-found", null);
                return;
            }
            if (readOnly) {
                org.bukkit.inventory.Inventory copy = org.bukkit.Bukkit.createInventory(null, chest.getType().getSize(),
                        messages.color("&8Read-Only Admin View"));
                copy.setContents(chest.getInventory().getContents());
                player.openInventory(copy);
            } else {
                player.openInventory(chest.getInventory());
            }
            messages.send(player, "admin-open", Map.of("player", target.getName(), "id", String.valueOf(chest.getId())));
        } else if (sub.equals("wipe")) {
            if (!player.hasPermission("chestlink.admin.wipe")) {
                messages.send(player, "no-permission", null);
                return;
            }
            if (args.length < 3) {
                messages.send(player, "admin-missing-id", null);
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messages.send(player, "admin-not-found", null);
                return;
            }
            BoundChest chest = manager.getOwnedChest(target, args[2]);
            if (chest != null) {
                manager.resetChest(chest);
                messages.send(player, "admin-wipe", Map.of("player", target.getName(), "id", String.valueOf(chest.getId())));
            } else {
                messages.send(player, "not-found", null);
            }
        } else if (sub.equals("delete")) {
            if (!player.hasPermission("chestlink.admin.delete")) {
                messages.send(player, "no-permission", null);
                return;
            }
            if (args.length < 3) {
                messages.send(player, "admin-missing-id", null);
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messages.send(player, "admin-not-found", null);
                return;
            }
            BoundChest chest = manager.getOwnedChest(target, args[2]);
            if (chest != null) {
                manager.deleteChest(target, chest);
                messages.send(player, "admin-delete", Map.of("player", target.getName(), "id", String.valueOf(chest.getId())));
            } else {
                messages.send(player, "not-found", null);
            }
        } else if (sub.equals("migrate")) {
            if (!player.hasPermission("chestlink.admin.migrate")) {
                messages.send(player, "no-permission", null);
                return;
            }
            manager.migrateAll();
            messages.send(player, "admin-migrate", null);
        } else if (sub.equals("purgebroken")) {
            if (!player.hasPermission("chestlink.admin.purge")) {
                messages.send(player, "no-permission", null);
                return;
            }
            int removed = manager.purgeBroken();
            messages.send(player, "admin-purge", Map.of("count", String.valueOf(removed)));
        } else {
            messages.send(player, "usage-admin", null);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(messages.color(messages.getPrefix() + "&eChestLink Commands"));
        player.sendMessage(messages.color("&6/chestlink bind <name>&7 - Start binding a chest you place and punch"));
        player.sendMessage(messages.color("&6/chestlink open <id|name>&7 - Open linked chest"));
        player.sendMessage(messages.color("&6/chestlink rename <id|oldName> <newName>&7 - Rename a chest"));
        player.sendMessage(messages.color("&6/chestlink reset <id|name>&7 - Wipe contents"));
        player.sendMessage(messages.color("&6/chestlink delete <id|name>&7 - Delete link"));
        player.sendMessage(messages.color("&6/chestlink share <id|name> <player>&7 - Share access to a chest"));
        player.sendMessage(messages.color("&6/chestlink info <id|name>&7 - View details"));
        player.sendMessage(messages.color("&6/chestlink limits&7 - View your limits"));
        player.sendMessage(messages.color("&6/chestlink tp <id|name>&7 - Teleport to chest (if enabled)"));
    }

    private int getLimit(Player player, InventoryType type) {
        if (player.isOp()) {
            return Integer.MAX_VALUE;
        }
        String base = type == InventoryType.SINGLE ? "chestlink.limit.single." : "chestlink.limit.double.";
        int best = type == InventoryType.SINGLE ? 1 : 0;
        for (int i = 1; i <= 64; i++) {
            if (player.hasPermission(base + i)) {
                best = Math.max(best, i);
            }
        }
        return best;
    }

    private boolean hasDoubleAccess(Player player) {
        return getLimit(player, InventoryType.DOUBLE) > 0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        PlayerData data = manager.getData(player);
        List<BoundChest> owned = new ArrayList<>(data.getChests());
        List<BoundChest> accessible = manager.getAccessibleChests(player);
        if (args.length == 1) {
            return Arrays.asList("bind", "open", "rename", "reset", "delete", "share", "info", "limits", "help", "tp", "admin", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return Arrays.asList("list", "open", "wipe", "delete", "migrate", "purgebroken");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            // /cl admin <sub> <player>
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase(Locale.ENGLISH);
            if (Arrays.asList("open", "wipe", "delete").contains(sub)) {
                Player target = Bukkit.getPlayer(args[2]);
                if (target != null) {
                    return manager.getData(target).getChests().stream()
                            .map(c -> String.valueOf(c.getId()))
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            } else if (sub.equals("open")) {
                return Arrays.asList("readonly");
            }
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ENGLISH);
            if (Arrays.asList("open", "info", "tp").contains(sub)) {
                return accessible.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.toList());
            }
            if (Arrays.asList("rename", "reset", "delete", "share").contains(sub)) {
                return owned.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("share")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void sendReloadButton(Player player) {
        String base = messages.msg("reloaded", null);
        TextComponent main = new TextComponent(TextComponent.fromLegacyText(base));
        TextComponent reload = new TextComponent(ChatColor.GRAY + " (" + ChatColor.YELLOW + "Reload" + ChatColor.GRAY + ")");
        reload.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chestlink reload"));
        reload.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent(ChatColor.YELLOW + "Click to reload again")}));
        main.addExtra(reload);
        player.spigot().sendMessage(main);
    }

    private void sendAdminList(Player viewer, Player target) {
        messages.send(viewer, "admin-list-header", Map.of("player", target.getName()));
        manager.getData(target).getChests().forEach(chest -> {
            TextComponent id = new TextComponent(ChatColor.GRAY + "#" + chest.getId() + " ");
            TextComponent name = new TextComponent(ChatColor.GOLD + chest.getName());
            name.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/chestlink admin open " + target.getName() + " " + chest.getId() + " readonly"));
            name.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponent[]{new TextComponent(ChatColor.YELLOW + "Click to view (read-only)")}));
            TextComponent type = new TextComponent(ChatColor.GRAY + " (" + chest.getType().getDisplayName() + ")");
            id.addExtra(name);
            id.addExtra(type);
            viewer.spigot().sendMessage(id);
        });
    }
}
