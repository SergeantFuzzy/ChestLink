package dev.sergeantfuzzy.chestlink.listener;

import dev.sergeantfuzzy.chestlink.BoundChest;
import dev.sergeantfuzzy.chestlink.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.InventoryType;
import dev.sergeantfuzzy.chestlink.PlayerData;
import dev.sergeantfuzzy.chestlink.gui.InventoryMenu;
import dev.sergeantfuzzy.chestlink.gui.ShareMenu;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Bukkit;
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

    public ChestEventsListener(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages, InventoryMenu menu, ShareMenu shareMenu) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.menu = menu;
        this.shareMenu = shareMenu;
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
        if (isUnsafePlacement(block)) {
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
                chest.markAccessed();
                manager.saveInventory(chest);
                player.openInventory(chest.getInventory());
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
        if (title.contains("Read-Only Admin View")) {
            event.setCancelled(true);
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
        BoundChest chest = manager.getByInventory(top);
        if (chest != null) {
            if (!manager.canModify(player, chest)) {
                event.setCancelled(true);
            } else {
                chest.markModified();
                manager.saveInventory(chest);
            }
        }
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
            chest.setName(newName);
            messages.send(player, "rename-success", Map.of("name", newName));
            data.setPendingRename(null);
            manager.save(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.save(event.getPlayer());
        manager.clearPending(event.getPlayer());
    }

    private boolean isUnsafePlacement(Block block) {
        if (block.getWorld().hasStorm()) {
            return true;
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
        Location loc = base.clone().add(0.5, 1.25, 0.5);
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
            Bukkit.getScheduler().runTaskLater(plugin, display::remove, 60L);
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
            Bukkit.getScheduler().runTaskLater(plugin, stand::remove, 60L);
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
        BoundChest chest = manager.getByInventory(inv);
        if (chest != null && manager.canModify(player, chest)) {
            chest.markModified();
            manager.saveInventory(chest);
        }
    }
}
