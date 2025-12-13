package dev.sergeantfuzzy.chestlink.gui;

import dev.sergeantfuzzy.chestlink.BoundChest;
import dev.sergeantfuzzy.chestlink.ChestFilter;
import dev.sergeantfuzzy.chestlink.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.FilterMode;
import dev.sergeantfuzzy.chestlink.FilterOverflowBehavior;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import dev.sergeantfuzzy.chestlink.upgrade.FilterSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FilterMenu {
    private static final String TITLE = "&8Filter Settings";
    private static final int MENU_SIZE = 27;
    private static final int TOGGLE_SLOT = 19;
    private static final int ADD_SLOT = 21;
    private static final int CLEAR_SLOT = 23;
    private static final int BACK_SLOT = 25;
    private static final int INFO_SLOT = 18;
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private final MessageService messages;
    private final Map<UUID, BoundChest> contexts = new HashMap<>();

    public FilterMenu(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public void open(Player player, BoundChest chest) {
        contexts.put(player.getUniqueId(), chest);
        player.openInventory(build(player, chest));
    }

    public boolean isFilterMenu(String title) {
        return ChatColor.stripColor(title).equalsIgnoreCase(ChatColor.stripColor(messages.color(TITLE)));
    }

    public void clear(Player player) {
        contexts.remove(player.getUniqueId());
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem) {
        BoundChest chest = contexts.get(player.getUniqueId());
        if (chest == null) return;
        if (slot == TOGGLE_SLOT) {
            toggleMode(player, chest);
            reopen(player, chest);
            return;
        }
        if (slot == ADD_SLOT) {
            addHeldItem(player, chest);
            reopen(player, chest);
            return;
        }
        if (slot == CLEAR_SLOT) {
            clearEntries(player, chest);
            reopen(player, chest);
            return;
        }
        if (slot == BACK_SLOT) {
            clear(player);
            plugin.upgradeMenu().open(player, chest);
            return;
        }
        if (slot >= 0 && slot <= 17) {
            List<Material> entries = new ArrayList<>(chest.getFilter().getEntries());
            if (slot < entries.size()) {
                removeEntry(player, chest, entries.get(slot));
                reopen(player, chest);
            }
            return;
        }
    }

    private void reopen(Player player, BoundChest chest) {
        contexts.put(player.getUniqueId(), chest);
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.openInventory(build(player, chest)), 1L);
    }

    private Inventory build(Player player, BoundChest chest) {
        Inventory inv = Bukkit.createInventory(player, MENU_SIZE, messages.color(TITLE));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(messages.color("&8"));
        fillerMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < MENU_SIZE; i++) {
            inv.setItem(i, filler);
        }
        ChestFilter filter = chest.getFilter();
        List<Material> entries = new ArrayList<>(filter.getEntries());
        for (int i = 0; i < 18 && i < entries.size(); i++) {
            Material mat = entries.get(i);
            ItemStack icon = new ItemStack(mat);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(messages.color("&c" + format(mat.name()) + " &7(Click to remove)"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }
        inv.setItem(INFO_SLOT, infoItem(filter, entries.size()));
        inv.setItem(TOGGLE_SLOT, toggleItem(filter));
        inv.setItem(ADD_SLOT, addItem(player));
        inv.setItem(CLEAR_SLOT, clearItem());
        inv.setItem(BACK_SLOT, backItem());
        return inv;
    }

    private ItemStack infoItem(ChestFilter filter, int count) {
        FilterSettings settings = plugin.upgradeSettings().getFilterSettings();
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color("&eCurrent Filter"));
        List<String> lore = new ArrayList<>();
        lore.add(messages.color("&7Mode: &f" + format(filter.getMode().name())));
        FilterOverflowBehavior overflow = settings.getOverflowBehavior();
        lore.add(messages.color("&7Overflow: &f" + format(overflow.name())));
        lore.add(messages.color("&7Entries: &f" + count + "&7/&f" + settings.getMaxEntries()));
        lore.add(messages.color("&7Whitelist allows only listed items."));
        lore.add(messages.color("&7Blacklist blocks listed items."));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggleItem(ChestFilter filter) {
        ItemStack item = new ItemStack(Material.LEVER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color("&6Toggle Mode"));
        List<String> lore = new ArrayList<>();
        lore.add(messages.color("&7Current: &f" + format(filter.getMode().name())));
        lore.add(messages.color("&aClick to switch."));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack addItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Material displayMat = held != null && held.getType() != Material.AIR ? held.getType() : Material.ITEM_FRAME;
        ItemStack item = new ItemStack(displayMat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color("&aAdd Held Item"));
        List<String> lore = new ArrayList<>();
        lore.add(messages.color("&7Hold an item and click to add"));
        lore.add(messages.color("&7its material to the filter."));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack clearItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color("&cClear Entries"));
        meta.setLore(List.of(messages.color("&7Remove all materials from filter.")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack backItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.color("&7Back"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void toggleMode(Player player, BoundChest chest) {
        ChestFilter filter = chest.getFilter();
        FilterMode next = filter.getMode() == FilterMode.WHITELIST ? FilterMode.BLACKLIST : FilterMode.WHITELIST;
        filter.setMode(next);
        manager.saveInventory(chest);
        messages.send(player, "filter-mode", Map.of("mode", next.name().toLowerCase()));
    }

    private void addHeldItem(Player player, BoundChest chest) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            messages.send(player, "filter-add-none", null);
            return;
        }
        FilterSettings settings = plugin.upgradeSettings().getFilterSettings();
        if (!chest.getFilter().add(held.getType(), settings.getMaxEntries())) {
            messages.send(player, "filter-add-failed", Map.of("item", format(held.getType().name())));
            return;
        }
        manager.saveInventory(chest);
        messages.send(player, "filter-add", Map.of("item", format(held.getType().name())));
    }

    private void removeEntry(Player player, BoundChest chest, Material material) {
        if (chest.getFilter().remove(material)) {
            manager.saveInventory(chest);
            messages.send(player, "filter-remove", Map.of("item", format(material.name())));
        }
    }

    private void clearEntries(Player player, BoundChest chest) {
        boolean changed = false;
        for (Material mat : new ArrayList<>(chest.getFilter().getEntries())) {
            changed |= chest.getFilter().remove(mat);
        }
        if (changed) {
            manager.saveInventory(chest);
            messages.send(player, "filter-cleared", null);
        }
    }

    private String format(String input) {
        String lower = input.toLowerCase().replace('_', ' ');
        String[] parts = lower.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }
}
