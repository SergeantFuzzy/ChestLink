package dev.sergeantfuzzy.chestlink.gui;

import dev.sergeantfuzzy.chestlink.BoundChest;
import dev.sergeantfuzzy.chestlink.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.InventoryType;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class InventoryMenu {
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private final MessageService messages;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy");
    private final Map<UUID, DeleteContext> pendingDeletes = new HashMap<>();
    private static final String CONFIRM_TITLE = "&8Confirm Deletion of Linked Chest";

    public InventoryMenu(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public Inventory build(Player player, int page) {
        List<BoundChest> chests = new ArrayList<>(manager.getAccessibleChests(player));

        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(chests.size() / (double) perPage));
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(player, 54, messages.color("&8ChestLink (" + (page + 1) + "/" + pages + ")"));

        int start = page * perPage;
        for (int slot = 0; slot < perPage; slot++) {
            int index = start + slot;
            if (index >= chests.size()) {
                break;
            }
            BoundChest chest = chests.get(index);
            inv.setItem(slot, toIcon(chest));
        }

        if (page > 0) {
            inv.setItem(45, nav(Material.ARROW, "&ePrevious Page"));
        }
        if (page < pages - 1) {
            inv.setItem(53, nav(Material.ARROW, "&eNext Page"));
        }
        inv.setItem(49, nav(Material.OAK_SIGN, "&aYour linked chests"));
        return inv;
    }

    private ItemStack nav(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toIcon(BoundChest chest) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color("&6" + chest.getName() + " &7(#" + chest.getId() + ")"));

        List<String> lore = new ArrayList<>();
        lore.add(messages.color("&7Last Modified: &f" + dateFormat.format(new Date(chest.getLastModified()))));
        lore.add(messages.color("&7Usage: &f" + chest.getUsedSlots() + "/" + chest.getInventory().getSize() + " Slots Used"));
        lore.add(messages.color("&7Owner: &f" + ownerName(chest.getOwner())));
        lore.add(messages.color("&7Shared With:"));
        if (chest.getShared().isEmpty()) {
            lore.add(messages.color("&8 - None"));
        } else {
            chest.getShared().forEach((uuid, access) -> {
                lore.add(messages.color("&8 - " + ownerName(uuid) + " (" + access.getAccessLevel().name().toLowerCase() + ")"));
            });
        }
        lore.add(messages.color("&7Type: &f" + (chest.getType() == InventoryType.SINGLE ? "Single" : "Double")));
        if (chest.getLocation() != null && chest.getLocation().getWorld() != null) {
            lore.add(messages.color("&7World: &6" + chest.getLocation().getWorld().getName()));
            lore.add(messages.color("&7Location: &6X: &e" + chest.getLocation().getBlockX()
                    + " &7| &6Y: &e" + chest.getLocation().getBlockY()
                    + " &7| &6Z: &e" + chest.getLocation().getBlockZ()));
        }
        lore.add(messages.color("&aLeft-Click: &7Open and view contents"));
        lore.add(messages.color("&eRight-Click: &7Reset this inventory"));
        lore.add(messages.color("&cShift-Left-Click: &7Delete this chest inventory (cannot be reversed)"));
        lore.add(messages.color("&6Shift-Right-Click: &7Rename this chest"));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(Player player, Inventory inventory, int slot, ClickType type, org.bukkit.event.inventory.InventoryAction action, int page) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        if (slot == 45 && inventory.getItem(slot) != null) {
            open(player, page - 1);
            return;
        }
        if (slot == 53 && inventory.getItem(slot) != null) {
            open(player, page + 1);
            return;
        }
        int index = page * 45 + slot;
        List<BoundChest> chests = new ArrayList<>(manager.getAccessibleChests(player));
        if (index >= chests.size()) {
            return;
        }
        BoundChest chest = chests.get(index);
        if (type == ClickType.LEFT) {
            if (!manager.canView(player, chest)) {
                messages.send(player, "no-permission", null);
                return;
            }
            if (manager.applyCapacity(chest)) {
                manager.saveInventory(chest);
            }
            player.openInventory(chest.getInventory());
            chest.markAccessed();
            manager.saveInventory(chest);
        } else if (type == ClickType.RIGHT) {
            if (!manager.canModify(player, chest)) {
                messages.send(player, "no-permission", null);
                return;
            }
            manager.resetChest(chest);
            messages.send(player, "reset", Map.of("name", chest.getName()));
            manager.save(player);
        } else if (type == ClickType.SHIFT_LEFT || type == ClickType.MIDDLE || type == ClickType.CREATIVE
                || type == ClickType.DROP || type == ClickType.CONTROL_DROP || type == ClickType.UNKNOWN
                || eventIsClone(action)) {
            if (!player.getUniqueId().equals(chest.getOwner())) {
                messages.send(player, "no-permission", null);
                return;
            }
            openDeleteConfirm(player, chest, page);
        } else if (type == ClickType.SHIFT_RIGHT) {
            if (!player.getUniqueId().equals(chest.getOwner())) {
                messages.send(player, "no-permission", null);
                return;
            }
            manager.getData(player).setPendingRename(chest.getId());
            player.closeInventory();
            messages.send(player, "rename-prompt", Map.of("name", chest.getName()));
        }
    }

    public void open(Player player, int page) {
        player.openInventory(build(player, page));
    }

    // Detect creative middle-click clone actions
    private boolean eventIsClone(org.bukkit.event.inventory.InventoryAction action) {
        return action == org.bukkit.event.inventory.InventoryAction.CLONE_STACK;
    }

    private String ownerName(UUID uuid) {
        org.bukkit.OfflinePlayer off = plugin.getServer().getOfflinePlayer(uuid);
        if (off != null && off.getName() != null) {
            return off.getName();
        }
        return uuid.toString().substring(0, 8);
    }

    public void openDeleteConfirm(Player player, BoundChest chest, int page) {
        Inventory inv = Bukkit.createInventory(player, 9, messages.color(CONFIRM_TITLE));
        inv.setItem(3, nav(Material.RED_STAINED_GLASS_PANE, "&cNo"));
        inv.setItem(5, nav(Material.GREEN_STAINED_GLASS_PANE, "&aYes"));
        pendingDeletes.put(player.getUniqueId(), new DeleteContext(chest, page));
        player.openInventory(inv);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory() != null && player.getOpenInventory().getTitle().equals(messages.color(CONFIRM_TITLE))) {
                player.closeInventory();
            }
            pendingDeletes.remove(player.getUniqueId());
        }, 100L);
    }

    public void handleDeleteConfirm(Player player, int slot) {
        DeleteContext context = pendingDeletes.remove(player.getUniqueId());
        if (context == null) {
            return;
        }
        if (slot == 5) {
            manager.deleteChest(player, context.chest());
            messages.send(player, "delete", Map.of("name", context.chest().getName()));
            int remaining = manager.getData(player).getChests().size();
            int pages = Math.max(1, (int) Math.ceil(remaining / 45.0));
            int newPage = Math.min(context.page(), pages - 1);
            open(player, newPage);
        } else {
            open(player, context.page());
        }
    }

    public boolean isConfirmInventory(String title) {
        return org.bukkit.ChatColor.stripColor(title).equalsIgnoreCase(org.bukkit.ChatColor.stripColor(messages.color(CONFIRM_TITLE)));
    }

    private record DeleteContext(BoundChest chest, int page) {
    }
}
