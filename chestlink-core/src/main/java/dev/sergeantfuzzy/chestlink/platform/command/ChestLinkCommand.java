package dev.sergeantfuzzy.chestlink.platform.command;

import dev.sergeantfuzzy.chestlink.core.data.BoundChest;
import dev.sergeantfuzzy.chestlink.core.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.core.data.InventoryType;
import dev.sergeantfuzzy.chestlink.core.data.PlayerData;
import dev.sergeantfuzzy.chestlink.core.license.ActivationResult;
import dev.sergeantfuzzy.chestlink.core.license.LicenseService;
import dev.sergeantfuzzy.chestlink.platform.gui.InventoryMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.ShareMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.UpgradeMenu;
import dev.sergeantfuzzy.chestlink.localization.MessageService;
import dev.sergeantfuzzy.chestlink.update.PluginMetadata;
import dev.sergeantfuzzy.chestlink.update.UpdateResult;
import dev.sergeantfuzzy.chestlink.update.UpdateService;
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
    private static final List<String> BIND_ALIASES = Arrays.asList("bind", "new", "create", "start");
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private final LicenseService licenseService;
    private static final String LICENSE_PREFIX = org.bukkit.ChatColor.DARK_GRAY + "" + org.bukkit.ChatColor.BOLD + "[" + org.bukkit.ChatColor.GOLD + "ChestLink" + org.bukkit.ChatColor.DARK_GRAY + org.bukkit.ChatColor.BOLD + "] " + org.bukkit.ChatColor.RESET;
    private MessageService messages;
    private InventoryMenu menu;
    private ShareMenu shareMenu;
    private UpgradeMenu upgradeMenu;
    private UpdateService updateService;
    private final PluginMetadata metadata;

    public ChestLinkCommand(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages, InventoryMenu menu, ShareMenu shareMenu, UpgradeMenu upgradeMenu, UpdateService updateService, PluginMetadata metadata) {
        this.plugin = plugin;
        this.manager = manager;
        this.licenseService = plugin.getLicenseService();
        this.messages = messages;
        this.menu = menu;
        this.shareMenu = shareMenu;
        this.upgradeMenu = upgradeMenu;
        this.updateService = updateService;
        this.metadata = metadata;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (args == null) {
            args = new String[0];
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("key")) {
            if (player == null) {
                sender.sendMessage(LICENSE_PREFIX + org.bukkit.ChatColor.RED + "Only players can activate licenses.");
                return true;
            }
            handleLicenseKey(player, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (isBetaLocked(player, null)) {
                return true;
            }
            if (!player.hasPermission("chestlink.menu")) {
                messages.send(player, "no-permission", null);
                return true;
            }
            menu.open(player, 0);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ENGLISH);
        if (!(sender instanceof Player)) {
            if (sub.equals("wiki")) {
                handleWiki(sender);
                return true;
            }
            if (sub.equals("support") || sub.equals("discord")) {
                handleSupport(sender);
                return true;
            }
            if (sub.equals("version")) {
                handleVersion(sender);
                return true;
            }
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("chestlink.use")) {
            messages.send(player, "no-permission", null);
            return true;
        }
        if (isBetaLocked(player, sub)) {
            return true;
        }
        switch (sub) {
            case "bind":
            case "new":
            case "create":
            case "start":
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
            case "upgrades":
                handleUpgrades(player, Arrays.copyOfRange(args, 1, args.length));
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
            case "wiki":
                handleWiki(sender);
                return true;
            case "support":
            case "discord":
                handleSupport(sender);
                return true;
            case "version":
                handleVersion(sender);
                return true;
            default:
                sendHelp(player);
                return true;
        }
    }

    private void handleLicenseKey(Player player, String[] args) {
        if (licenseService == null) {
            player.sendMessage("§cLicense system is unavailable. Please contact an administrator.");
            return;
        }
        if (licenseService.isLicensed()) {
            player.sendMessage("§cChestLink-Beta license is already active. It cannot be disabled or re-activated.");
            Bukkit.getConsoleSender().sendMessage(LICENSE_PREFIX + "§c" + player.getName() + " attempted to activate a license key while a license is already active.");
            return;
        }
        if (args.length == 0) {
            player.sendMessage("§cUsage: /chestlink key <LICENSE-KEY>");
            return;
        }
        String provided = args[0].trim().toUpperCase(Locale.ENGLISH);
        player.sendMessage("§7Validating license key, please wait...");
        licenseService.activateLicenseAsync(player, provided).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> handleActivationResult(player, provided, result))
        );
    }

    private void handleActivationResult(Player player, String provided, ActivationResult result) {
        if (result == null) {
            player.sendMessage("§cLicense activation failed: Unexpected error.");
            return;
        }
        if (result.getStatus() == ActivationResult.Status.INVALID_FORMAT) {
            player.sendMessage("§cInvalid license key format. Expected: XXXXXX-XXXXX-XXXXXX.");
            String prefix = plugin.isLicenseEnforced() ? "[ChestLink Beta]" : "[ChestLink]";
            plugin.getLogger().info(prefix + " Invalid license key format entered by " + player.getName());
            return;
        }
        if (result.isSuccess()) {
            player.sendMessage("§aChestLink-Beta license activated. Thank you for supporting the project!");
            if (plugin.isLicenseEnforced()) {
                Bukkit.getConsoleSender().sendMessage(LICENSE_PREFIX + "§aLicense key activated successfully. §fPlugin unlocked.");
                Bukkit.getOnlinePlayers().stream()
                        .filter(Player::isOp)
                        .forEach(op -> op.sendMessage("§6[ChestLink] ChestLink-Beta license activated on this server."));
            } else {
                plugin.getLogger().info("License key activated successfully.");
            }
            return;
        }
        String message = result.getMessage() == null ? "Unknown error" : result.getMessage();
        player.sendMessage("§cLicense activation failed: " + message);
        if (plugin.isLicenseEnforced()) {
            Bukkit.getConsoleSender().sendMessage(LICENSE_PREFIX + "§cLicense activation failed: §e" + message);
        } else {
            plugin.getLogger().warning("License activation failed: " + message);
        }
    }

    private boolean isBetaLocked(CommandSender sender, String attemptedSubcommand) {
        if (!plugin.isLicenseEnforced()) {
            return false;
        }
        if ("key".equalsIgnoreCase(String.valueOf(attemptedSubcommand))) {
            return false;
        }
        if (plugin.isLicenseActive()) {
            return false;
        }
        if (sender instanceof Player p) {
            p.sendMessage("§cChestLink Beta is currently locked. Use §e/chestlink key <LICENSE-KEY> §cto unlock.");
        }
        return true;
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
        manager.applyCapacity(chest);
        manager.applyAutoSort(chest);
        manager.saveInventory(chest);
        manager.openPage(player, chest, 0);
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

    private void handleUpgrades(Player player, String[] args) {
        if (!hasUpgradeUsePermission(player)) {
            messages.send(player, "no-permission", null);
            return;
        }
        List<BoundChest> accessible = manager.getAccessibleChests(player);
        if (args.length < 1) {
            if (accessible.isEmpty()) {
                sendNoLinkedChestsMessage(player);
            } else {
                messages.send(player, "usage-upgrades", null);
            }
            return;
        }
        BoundChest chest = manager.getOwnedChest(player, args[0]);
        if (chest == null && senderHasManageOthers(player)) {
            chest = manager.getAccessibleChest(player, args[0]);
        }
        if (chest == null) {
            messages.send(player, "not-found", null);
            return;
        }
        if (!player.getUniqueId().equals(chest.getOwner()) && !senderHasManageOthers(player)) {
            messages.send(player, "no-permission", null);
            return;
        }
        upgradeMenu.open(player, chest);
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
        player.sendMessage(messages.color("&7Type: &6" + chest.getType().getDisplayName() + " &7Slots: &6" + chest.getCapacity()));
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
        this.upgradeMenu = plugin.upgradeMenu();
        this.updateService = plugin.updateService();
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
            if (manager.applyCapacity(chest)) {
                manager.saveInventory(chest);
            }
            manager.setReadOnly(player, readOnly);
            manager.openPage(player, chest, 0);
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
        player.sendMessage(" "); // spacer before help header
        player.sendMessage(messages.color("&8&l------------------------------"));
        player.sendMessage(messages.color(messages.getPrefix() + "&eChestLink Commands"));
        sendSuggestHelp(player, "/chestlink bind|new|create|start <name>", "Start binding a chest you place and punch");
        sendSuggestHelp(player, "/chestlink open <id|name>", "Open linked chest");
        sendSuggestHelp(player, "/chestlink rename <id|oldName> <newName>", "Rename a chest");
        sendSuggestHelp(player, "/chestlink reset <id|name>", "Wipe contents");
        sendSuggestHelp(player, "/chestlink delete <id|name>", "Delete link");
        sendSuggestHelp(player, "/chestlink share <id|name> <player>", "Share access to a chest");
        sendSuggestHelp(player, "/chestlink upgrades <id|name>", "Manage upgrades for a chest");
        sendSuggestHelp(player, "/chestlink info <id|name>", "View details");
        sendSuggestHelp(player, "/chestlink limits", "View your limits");
        sendSuggestHelp(player, "/chestlink tp <id|name>", "Teleport to chest (if enabled)");
        sendSuggestHelp(player, "/chestlink admin", "Admin commands");
        sendSuggestHelp(player, "/chestlink wiki", "Open the ChestLink wiki");
        sendSuggestHelp(player, "/chestlink support|discord", "Join support Discord");
        sendSuggestHelp(player, "/chestlink version", "Show version info");
        player.sendMessage(messages.color("&8&l------------------------------"));
    }

    private void sendSuggestHelp(Player player, String commandText, String description) {
        TextComponent commandComponent = new TextComponent(ChatColor.GOLD + commandText);
        commandComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandText));
        commandComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent(ChatColor.YELLOW + "Click to suggest " + commandText)}));

        TextComponent descComponent = new TextComponent(ChatColor.GRAY + " - " + description);

        TextComponent line = new TextComponent();
        line.addExtra(commandComponent);
        line.addExtra(descComponent);
        player.spigot().sendMessage(line);
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
            List<String> base = new ArrayList<>();
            base.add("key");
            base.addAll(BIND_ALIASES);
            base.addAll(Arrays.asList("open", "rename", "reset", "delete", "share", "upgrades", "info", "limits", "help", "tp", "admin", "reload", "wiki", "support", "discord", "version"));
            return base;
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
            if (Arrays.asList("rename", "reset", "delete", "share", "upgrades").contains(sub)) {
                if (sub.equals("upgrades") && senderHasManageOthers(player)) {
                    return accessible.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.toList());
                }
                return owned.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("share")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private boolean hasUpgradeUsePermission(Player player) {
        if (player == null) {
            return false;
        }
        return player.hasPermission("chestlink.upgrades.use")
                || player.hasPermission("chestlink.upgrades");
    }

    private boolean senderHasManageOthers(Player player) {
        if (player == null) {
            return false;
        }
        return player.hasPermission("chestlink.upgrades.manage.others")
                || player.hasPermission("chestlink.admin");
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

    private void handleWiki(CommandSender sender) {
        if (!sender.hasPermission("chestlink.wiki")) {
            messages.send(sender, "no-permission", null);
            return;
        }
        String url = "https://sergeantfuzzy.dev/wiki/plugins/chestlink/";
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.DARK_GRAY + "] " + ChatColor.YELLOW + "ChestLink Wiki: " + ChatColor.AQUA + url);
            return;
        }
        sendClickable(sender,
                ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.DARK_GRAY + "] " + ChatColor.YELLOW + "Open the ChestLink Wiki: ",
                ChatColor.AQUA + "" + ChatColor.UNDERLINE + "[Wiki]",
                url,
                ChatColor.YELLOW + "Click to open the ChestLink Wiki (setup guides, FAQs, and tutorials)");
    }

    private void handleSupport(CommandSender sender) {
        if (!sender.hasPermission("chestlink.support")) {
            messages.send(sender, "no-permission", null);
            return;
        }
        String url = "https://discord.gg/invite/Ns6p9DnjGs";
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.DARK_GRAY + "] " + ChatColor.YELLOW + "ChestLink Discord: " + ChatColor.AQUA + url);
            return;
        }
        sendClickable(sender,
                ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.DARK_GRAY + "] " + ChatColor.YELLOW + "Join the support Discord: ",
                ChatColor.AQUA + "" + ChatColor.UNDERLINE + "[Discord]",
                url,
                ChatColor.YELLOW + "Click to join the ChestLink support Discord for help and updates");
    }

    private void handleVersion(CommandSender sender) {
        if (!sender.hasPermission("chestlink.version")) {
            messages.send(sender, "no-permission", null);
            return;
        }
        UpdateService updates = this.updateService;
        if (updates != null && updates.isCheckEnabled()) {
            sender.sendMessage(messages.color("&7[&6ChestLink&7]&r &eChecking for updates..."));
            updates.checkNowAsync(false).thenAccept(result ->
                    Bukkit.getScheduler().runTask(plugin, () -> sendVersionDetails(sender, result))
            );
        } else {
            sendVersionDetails(sender, updates != null ? Optional.ofNullable(updates.getLastResult()).orElse(UpdateResult.unknown(plugin.getDescription().getVersion())) : UpdateResult.unknown(plugin.getDescription().getVersion()));
        }
    }

    private void sendVersionDetails(CommandSender sender, UpdateResult result) {
        String version = plugin.getDescription().getVersion();
        String latest = result.getLatestVersion();
        UpdateResult.Status status = result.getStatus();
        boolean isPlayer = sender instanceof Player;
        String divider = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "------------------------------";
        if (!isPlayer) {
            sender.sendMessage(divider);
            sender.sendMessage(ChatColor.GOLD + "ChestLink " + ChatColor.WHITE + version);
            sender.sendMessage(ChatColor.GRAY + "Release date: " + ChatColor.WHITE + (metadata == null ? "Unknown" : metadata.getReleaseDate()));
            sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + (metadata == null ? plugin.getDescription().getDescription() : metadata.getDescription()));
            if (status == UpdateResult.Status.OUTDATED) {
                sender.sendMessage(ChatColor.RED + "New version available: " + ChatColor.WHITE + latest);
                String url = Optional.ofNullable(result.bestUrl()).orElse("https://github.com/SergeantFuzzy/ChestLink/releases/latest");
                sender.sendMessage(ChatColor.AQUA + "Download: " + ChatColor.AQUA + "" + ChatColor.UNDERLINE + url);
            } else {
                sender.sendMessage(ChatColor.GREEN + "Running latest (or newer) build.");
                String url = "https://sergeantfuzzy.dev/wiki/plugins/chestlink/";
                sender.sendMessage(ChatColor.YELLOW + "Wiki Page: " + ChatColor.AQUA + url);
            }
            sender.sendMessage(divider);
            return;
        }

        sender.sendMessage(divider);
        sender.sendMessage(ChatColor.GOLD + "ChestLink " + ChatColor.WHITE + version);
        sender.sendMessage(ChatColor.GRAY + "Release date: " + ChatColor.WHITE + (metadata == null ? "Unknown" : metadata.getReleaseDate()));
        sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + (metadata == null ? plugin.getDescription().getDescription() : metadata.getDescription()));

        if (status == UpdateResult.Status.OUTDATED) {
            sender.sendMessage(ChatColor.RED + "New version available: " + ChatColor.WHITE + latest);
            String url = Optional.ofNullable(result.bestUrl()).orElse("https://github.com/SergeantFuzzy/ChestLink/releases/latest");
            sendClickable(sender, ChatColor.YELLOW + "Download: ", ChatColor.AQUA + "" + ChatColor.UNDERLINE + "[Open Page]", url,
                    ChatColor.YELLOW + "Click to open the latest ChestLink download page");
            Player player = (Player) sender;
            player.sendTitle(ChatColor.GOLD + "ChestLink", ChatColor.RED + "New version available!", 10, 60, 20);
        } else {
            sender.sendMessage(ChatColor.GREEN + "Running latest (or newer) build.");
            String url = "https://sergeantfuzzy.dev/wiki/plugins/chestlink/";
            sendClickable(sender, ChatColor.YELLOW + "Wiki: ", ChatColor.AQUA + "" + ChatColor.UNDERLINE + "[View Wiki]",
                    url,
                    ChatColor.YELLOW + "Click to open the ChestLink Wiki");
            Player player = (Player) sender;
            player.sendTitle(ChatColor.GOLD + "ChestLink", ChatColor.GREEN + "You're up to date! v" + version, 10, 60, 20);
        }
        sender.sendMessage(divider);
    }

    private void sendClickable(CommandSender sender, String prefix, String buttonText, String url, String hover) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + ChatColor.AQUA + "" + ChatColor.UNDERLINE + url);
            return;
        }
        TextComponent base = new TextComponent(prefix);
        TextComponent button = new TextComponent(buttonText);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent(hover)}));
        base.addExtra(button);
        ((Player) sender).spigot().sendMessage(base);
    }

    private void sendNoLinkedChestsMessage(Player player) {
        List<String> hover = List.of(
                "&8&m---------------------------&r",
                "&r&6How to create a linked chest",
                "&8&m---------------------------&r",
                "&r&7 1) &eHold a chest &7and run &6/cl bind",
                "&r&7 2) &ePlace &7the chest then &eleft-click &7it",
                "&r&7 3) &eOpen &7it with &6/cl open <id>",
                "&8&m---------------------------&r"
        );
        messages.sendWithHover(player, "no-linked-chests", null, hover);
    }
}
