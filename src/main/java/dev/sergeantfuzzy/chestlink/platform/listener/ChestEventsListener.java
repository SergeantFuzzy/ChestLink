package dev.sergeantfuzzy.chestlink.platform.listener;

import dev.sergeantfuzzy.chestlink.core.data.BoundChest;
import dev.sergeantfuzzy.chestlink.core.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.core.data.InventoryType;
import dev.sergeantfuzzy.chestlink.core.data.PlayerData;
import dev.sergeantfuzzy.chestlink.core.data.ChestInventoryView;
import dev.sergeantfuzzy.chestlink.platform.gui.FilterMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.InventoryMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.ShareMenu;
import dev.sergeantfuzzy.chestlink.platform.gui.UpgradeMenu;
import dev.sergeantfuzzy.chestlink.localization.MessageService;
import dev.sergeantfuzzy.chestlink.platform.util.ChestPositionUtil;
import dev.sergeantfuzzy.chestlink.platform.compat.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChestEventsListener implements Listener {
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private final MessageService messages;
    private final InventoryMenu menu;
    private final ShareMenu shareMenu;
    private final UpgradeMenu upgradeMenu;
    private final FilterMenu filterMenu;

    public ChestEventsListener(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages, InventoryMenu menu, ShareMenu shareMenu, UpgradeMenu upgradeMenu, FilterMenu filterMenu) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.menu = menu;
        this.shareMenu = shareMenu;
        this.upgradeMenu = upgradeMenu;
        this.filterMenu = filterMenu;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) {
            return;
        }
        if (manager.getPendingBind(player) == null) return; // only guard when binding
        if (!player.hasPermission("chestlink.bind")) return;
        if (event.getBlockPlaced().getType() != Material.CHEST && event.getBlockPlaced().getType() != Material.TRAPPED_CHEST) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            messages.send(player, "placement-deny-mode", null);
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlockPlaced();
        boolean safeEnabled = plugin.getConfig().getBoolean("safe-placement-enabled", true);
        if (safeEnabled && isUnsafePlacement(block)) {
            messages.send(player, "placement-unsafe", null);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        if (!(block.getState() instanceof Chest)) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerData data = manager.getData(player);
            for (BoundChest chest : data.getChests()) {
                if (chest.matches(block.getLocation())) {
                    manager.deleteChest(player, chest);
                    messages.send(player, "broken", Map.of("name", chest.getName()));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();
        boolean isChest = type == Material.CHEST || type == Material.TRAPPED_CHEST;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && isChest && player.hasPermission("chestlink.bind") && manager.getPendingBind(player) != null) {
            // finalize binding
            if (manager.getChest(player, block.getLocation()) != null) {
                return;
            }
            BoundChest bound = manager.bind(player, block.getLocation());
            if (bound != null) {
                if (block.getState() instanceof Chest state) {
                    state.getBlockInventory().clear();
                    state.update(true);
                }
                showHologram(block.getLocation(), messages.getPrefix() + ChatColor.GREEN + "Linked " + bound.getType().getDisplayName());
                event.setCancelled(true);
            } else {
                showHologram(block.getLocation(), messages.getPrefix() + ChatColor.RED + "Link failed");
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isChest) {
            BoundChest chest = manager.getChest(player, block.getLocation());
            if (chest != null) {
                event.setCancelled(true);
                if (!manager.canView(player, chest)) {
                    messages.send(player, "no-permission", null);
                    return;
                }
                manager.applyCapacity(chest);
                manager.openPage(player, chest, 0);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top == null) {
            return;
        }
        String title = event.getView().getTitle();
        if (shareMenu.isShareMenu(title)) {
            event.setCancelled(true);
            shareMenu.handleClick(player, event.getRawSlot());
            return;
        }
        if (menu.isConfirmInventory(title)) {
            event.setCancelled(true);
            menu.handleDeleteConfirm(player, event.getRawSlot());
            return;
        }
        if (title.contains("ChestLink")) {
            event.setCancelled(true);
            int page = extractPage(title);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)) {
                menu.handleClick(player, top, event.getRawSlot(), event.getClick(), event.getAction(), page);
            }
            return;
        }
        if (upgradeMenu.isUpgradeMenu(title)) {
            event.setCancelled(true);
            upgradeMenu.handleClick(player, event.getRawSlot(), event.getClick());
            return;
        }
        if (filterMenu.isFilterMenu(title)) {
            event.setCancelled(true);
            filterMenu.handleClick(player, event.getRawSlot(), event.getCurrentItem());
            return;
        }
        ChestInventoryView view = manager.getView(top);
        if (view != null) {
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
                return;
            }
            BoundChest chest = view.chest();
            int rawSlot = event.getRawSlot();
            if (view.isPrev(rawSlot)) {
                event.setCancelled(true);
                manager.changePage(player, view, -1);
                return;
            }
            if (view.isNext(rawSlot)) {
                event.setCancelled(true);
                manager.changePage(player, view, 1);
                return;
            }
            if (view.isInfo(rawSlot) || view.isBlocked(rawSlot)) {
                event.setCancelled(true);
                return;
            }
            if (!manager.canModify(player, chest)) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> manager.syncInventoryView(top, player));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top == null) {
            return;
        }
        if (filterMenu.isFilterMenu(event.getView().getTitle())) {
            event.setCancelled(true);
            return;
        }
        ChestInventoryView view = manager.getView(top);
        if (view == null) {
            return;
        }
        boolean affectsTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        boolean touchesBlocked = event.getRawSlots().stream().anyMatch(view::isBlocked);
        if (!affectsTop || touchesBlocked) {
            event.setCancelled(touchesBlocked);
            return;
        }
        if (!manager.canModify(player, view.chest())) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> manager.syncInventoryView(top, player));
    }

    @EventHandler
    public void onChatRename(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = manager.getData(player);
        Integer renameId = data.getPendingRename();
        if (renameId == null) {
            return;
        }
        event.setCancelled(true);
        String newName = event.getMessage().trim();
        BoundChest chest = data.getByIdOrName(String.valueOf(renameId));
        if (chest != null) {
            manager.renameChest(player, chest, newName);
            messages.sendClickableRename(player, chest, menu.describeChest(chest));
            data.setPendingRename(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.save(event.getPlayer());
        manager.clearPending(event.getPlayer());
        manager.setReadOnly(event.getPlayer(), false);
    }

    private boolean isUnsafePlacement(Block block) {
        if (block.getWorld().hasStorm()) {
            int highest = block.getWorld().getHighestBlockYAt(block.getLocation());
            if (block.getY() >= highest) {
                return true; // exposed to rain/snow
            }
        }
        Material typeBelow = block.getRelative(BlockFace.DOWN).getType();
        if (typeBelow == Material.WATER || typeBelow == Material.LAVA) return true;
        Material type = block.getType();
        if (type == Material.MOVING_PISTON || type == Material.PISTON_HEAD) return true;
        if (block.getRelative(BlockFace.DOWN).getType().toString().contains("SLIME")
                || block.getRelative(BlockFace.DOWN).getType().toString().contains("MAGMA")) {
            return true;
        }
        return false;
    }

    private void showHologram(Location base, String text) {
        if (base == null || base.getWorld() == null) return;
        if (!plugin.getConfig().getBoolean("holograms-enabled", true)) return;
        Location loc = dev.sergeantfuzzy.chestlink.platform.util.ChestPositionUtil.center(base);
        if (loc == null) return;
        loc.add(0.0, 0.75, 0.0);
        try {
            Component comp = LegacyComponentSerializer.legacySection().deserialize(text);
            String legacy = LegacyComponentSerializer.legacySection().serialize(comp);
            TextDisplay display = base.getWorld().spawn(loc, TextDisplay.class, spawned -> {
                spawned.setText(legacy);
                spawned.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                spawned.setSeeThrough(false);
                spawned.setShadowed(false);
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            SchedulerCompat.runLocationTaskLater(plugin, base, display::remove, 60L);
        } catch (Exception ignored) {
            ArmorStand stand = base.getWorld().spawn(loc, ArmorStand.class, spawned -> {
                spawned.setVisible(false);
                spawned.setMarker(true);
                spawned.setGravity(false);
                spawned.setSmall(true);
                spawned.setBasePlate(false);
                spawned.setArms(false);
                spawned.setCollidable(false);
                spawned.setCustomName(text);
                spawned.setCustomNameVisible(true);
            });
            SchedulerCompat.runLocationTaskLater(plugin, base, stand::remove, 60L);
        }
    }

    private int extractPage(String title) {
        try {
            String[] parts = ChatColor.stripColor(title).split("[/)]");
            String[] left = parts[0].split("\\(");
            return Integer.parseInt(left[left.length - 1].trim()) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        String title = event.getView().getTitle();
        if (shareMenu.isShareMenu(title)) {
            shareMenu.clear(player);
        }
        if (upgradeMenu.isUpgradeMenu(title)) {
            upgradeMenu.clear(player);
        }
        if (filterMenu.isFilterMenu(title)) {
            filterMenu.clear(player);
        }
        ChestInventoryView view = manager.getView(inv);
        if (view != null) {
            if (manager.canModify(player, view.chest())) {
                manager.syncInventoryView(inv, player);
            }
            manager.clearView(inv);
            manager.setReadOnly(player, false);
        }
    }
}
