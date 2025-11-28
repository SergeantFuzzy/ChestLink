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

    public InventoryMenu(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public Inventory build(Player player, int page) {
        List<BoundChest> chests = new ArrayList<>(manager.getData(player).getChests());
        chests.sort(Comparator.comparingInt(BoundChest::getId));

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
        lore.add(messages.color("&7Type: &6" + chest.getType().getDisplayName()));
        lore.add(messages.color("&7Slots: &6" + chest.getType().getSize()));
        lore.add(messages.color("&7Created: &6" + dateFormat.format(new Date(chest.getCreatedAt()))));
        if (chest.getLocation() != null && chest.getLocation().getWorld() != null) {
            lore.add(messages.color("&7World: &6" + chest.getLocation().getWorld().getName()));
            lore.add(messages.color("&7Location: &6X: &e" + chest.getLocation().getBlockX()
                    + " &7| &6Y: &e" + chest.getLocation().getBlockY()
                    + " &7| &6Z: &e" + chest.getLocation().getBlockZ()));
        }
        lore.add(messages.color("&aLeft-Click: &7Open and view contents"));
        lore.add(messages.color("&eRight-Click: &7Reset this inventory"));
        lore.add(messages.color("&cMiddle-Click: &7Delete this chest inventory (cannot be reversed)"));
        lore.add(messages.color("&6Shift-Right-Click: &7Rename this chest"));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(Player player, Inventory inventory, int slot, ClickType type, int page) {
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
        List<BoundChest> chests = new ArrayList<>(manager.getData(player).getChests());
        chests.sort(Comparator.comparingInt(BoundChest::getId));
        if (index >= chests.size()) {
            return;
        }
        BoundChest chest = chests.get(index);
        if (type == ClickType.LEFT) {
            player.openInventory(chest.getInventory());
            chest.markAccessed();
        } else if (type == ClickType.RIGHT) {
            manager.resetChest(chest);
            messages.send(player, "reset", Map.of("name", chest.getName()));
            manager.save(player);
        } else if (type == ClickType.MIDDLE || type == ClickType.CREATIVE) {
            manager.deleteChest(player, chest);
            messages.send(player, "delete", Map.of("name", chest.getName()));
            manager.save(player);
            int remaining = manager.getData(player).getChests().size();
            int pages = Math.max(1, (int) Math.ceil(remaining / 45.0));
            int newPage = Math.min(page, pages - 1);
            open(player, newPage);
        } else if (type == ClickType.SHIFT_RIGHT) {
            manager.getData(player).setPendingRename(chest.getId());
            player.closeInventory();
            messages.send(player, "rename-prompt", Map.of("name", chest.getName()));
        }
    }

    public void open(Player player, int page) {
        player.openInventory(build(player, page));
    }
}
