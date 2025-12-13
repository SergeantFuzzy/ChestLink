package dev.sergeantfuzzy.chestlink.platform.gui;

import dev.sergeantfuzzy.chestlink.core.data.BoundChest;
import dev.sergeantfuzzy.chestlink.core.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.features.upgrade.CapacitySettings;
import dev.sergeantfuzzy.chestlink.features.upgrade.ChestUpgradeType;
import dev.sergeantfuzzy.chestlink.features.upgrade.FilterSettings;
import dev.sergeantfuzzy.chestlink.features.upgrade.UpgradeConfigEntry;
import dev.sergeantfuzzy.chestlink.features.upgrade.UpgradeCost;
import dev.sergeantfuzzy.chestlink.features.upgrade.UpgradeLimitSettings;
import dev.sergeantfuzzy.chestlink.platform.economy.EconomyBridge;
import dev.sergeantfuzzy.chestlink.localization.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class UpgradeMenu {
    private static final String TITLE = "&8Chest Upgrades";
    private static final int MENU_SIZE = 27;
    private static final Map<Integer, ChestUpgradeType> BUTTON_SLOTS = Map.ofEntries(
            Map.entry(10, ChestUpgradeType.CAPACITY),
            Map.entry(12, ChestUpgradeType.AUTO_SORT),
            Map.entry(14, ChestUpgradeType.FILTER),
            Map.entry(16, ChestUpgradeType.COMPRESSION)
    );
    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private final MessageService messages;
    private final Map<UUID, BoundChest> contexts = new HashMap<>();
    private final DecimalFormat economyFormat = new DecimalFormat("#,##0.00");

    public UpgradeMenu(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    public void open(Player player, BoundChest chest) {
        Inventory inv = Bukkit.createInventory(player, MENU_SIZE, messages.color(TITLE));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(messages.color("&8"));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < MENU_SIZE; i++) {
            inv.setItem(i, filler);
        }
        BUTTON_SLOTS.forEach((slot, type) -> inv.setItem(slot, buildIcon(type, chest, player)));
        contexts.put(player.getUniqueId(), chest);
        player.openInventory(inv);
    }

    public boolean isUpgradeMenu(String title) {
        return ChatColor.stripColor(title).equalsIgnoreCase(ChatColor.stripColor(messages.color(TITLE)));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        BoundChest chest = contexts.get(player.getUniqueId());
        if (chest == null) {
            return;
        }
        if (click != null && click.isRightClick()) {
            return;
        }
        ChestUpgradeType type = BUTTON_SLOTS.get(slot);
        if (type == null) {
            return;
        }
        if (type == ChestUpgradeType.FILTER && chest.getUpgradeLevel(type) > 0) {
            plugin.filterMenu().open(player, chest);
            return;
        }
        boolean upgraded = attemptUpgrade(player, chest, type);
        if (upgraded && type == ChestUpgradeType.FILTER) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.filterMenu().open(player, chest));
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, chest), 1L);
    }

    public void clear(Player player) {
        contexts.remove(player.getUniqueId());
    }

    private ItemStack buildIcon(ChestUpgradeType type, BoundChest chest, Player player) {
        Material icon = Material.BOOK;
        if (plugin.upgrades().get(type) != null) {
            icon = plugin.upgrades().get(type).getIcon();
        }
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(upgradeName(type));
        List<String> lore = new ArrayList<>();
        int level = chest.getUpgradeLevel(type);
        int max = type.getMaxLevel();
        lore.add(messages.color("&7Level: &e" + level + "&7/&e" + max));
        addDescriptionLore(lore, type);
        switch (type) {
            case CAPACITY -> {
                lore.add(messages.color("&7Current Size: &f" + chest.getCapacity() + " slots"));
                if (level < max) {
                    CapacitySettings settings = plugin.upgradeSettings().getCapacitySettings();
                    int nextSize = settings.sizeForLevel(level + 1, chest.getType(), chest.getCapacity());
                    lore.add(messages.color("&7Next Size: &a" + nextSize + " slots"));
                }
            }
            case AUTO_SORT -> {
                String strategy = plugin.upgradeSettings().getAutoSortSettings().getStrategy().name().replace('_', ' ');
                lore.add(messages.color("&7Strategy: &f" + capitalize(strategy)));
                lore.add(messages.color("&7Keeps contents sorted automatically."));
                if (level > 0) {
                    lore.add(messages.color("&aCurrently active."));
                }
            }
            case COMPRESSION -> {
                lore.add(messages.color("&7Auto-crafts compressible items"));
                lore.add(messages.color("&7into block form on insert."));
                lore.add(messages.color("&7Your XP Level: &e" + player.getLevel()));
                if (level > 0) {
                    lore.add(messages.color("&aActive recipes:"));
                    plugin.upgradeSettings().getCompressionSettings().recipes().forEach((input, output) ->
                            lore.add(messages.color("&8 - &f" + formatMaterial(input) + " &7-> &f" + formatMaterial(output))));
                }
            }
            case FILTER -> {
                FilterSettings settings = plugin.upgradeSettings().getFilterSettings();
                lore.add(messages.color("&7Mode: &f" + capitalize(chest.getFilter().getMode().name().replace('_', ' '))));
                lore.add(messages.color("&7Entries: &f" + chest.getFilter().getEntries().size()
                        + "&7/&f" + settings.getMaxEntries()));
                lore.add(messages.color("&7Overflow: &f" + capitalize(settings.getOverflowBehavior().name().replace('_', ' '))));
                if (level > 0) {
                    lore.add(messages.color("&aClick to configure filters."));
                }
            }
            default -> {
            }
        }
        if (!plugin.upgrades().isEnabled(type)) {
            lore.add(messages.color("&cThis upgrade is disabled."));
        } else if (level >= max) {
            lore.add(messages.color("&8&m----------------------"));
            lore.add(messages.color("&cMax level unlocked!"));
        } else {
            UpgradeCost cost = costForLevel(type, level + 1);
            appendCostLore(lore, cost, player);
            lore.add(messages.color("&aLeft-click to upgrade."));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private boolean attemptUpgrade(Player player, BoundChest chest, ChestUpgradeType type) {
        String upgradeName = ChatColor.stripColor(upgradeName(type));
        if (!player.getUniqueId().equals(chest.getOwner())) {
            messages.send(player, "upgrade-owner-only", null);
            return false;
        }
        if (!plugin.upgrades().isEnabled(type)) {
            messages.send(player, "upgrade-disabled", null);
            return false;
        }
        if (!player.hasPermission("chestlink.upgrades." + type.getKey())) {
            messages.send(player, "upgrade-no-permission-upgrade", Map.of("upgrade", upgradeName));
            return false;
        }
        int level = chest.getUpgradeLevel(type);
        int max = type.getMaxLevel();
        if (level >= max) {
            messages.send(player, "upgrade-max-level", null);
            return false;
        }
        UpgradeLimitSettings limits = plugin.upgradeSettings().getLimitSettings();
        if (limits.isEnabled() && !limits.canBypass(player)) {
            int maxLevel = limits.maxLevel(player, type);
            if (maxLevel > 0 && level >= maxLevel) {
                messages.send(player, "upgrade-limit-level", Map.of("upgrade", upgradeName, "limit", String.valueOf(maxLevel)));
                return false;
            }
            int maxChests = limits.maxUpgradedChests(player);
            if (maxChests > 0 && level == 0) {
                long unlocked = manager.getData(player).getChests().stream()
                        .filter(c -> c.getUpgradeLevel(type) > 0)
                        .count();
                if (unlocked >= maxChests) {
                    messages.send(player, "upgrade-limit-count", Map.of("upgrade", upgradeName, "limit", String.valueOf(maxChests)));
                    return false;
                }
            }
        }
        UpgradeCost cost = costForLevel(type, level + 1);
        if (!processPayment(player, type, cost)) {
            return false;
        }
        chest.setUpgradeLevel(type, level + 1);
        if (type == ChestUpgradeType.CAPACITY) {
            manager.applyCapacity(chest);
        } else if (type == ChestUpgradeType.AUTO_SORT) {
            manager.applyAutoSort(chest);
        } else if (type == ChestUpgradeType.COMPRESSION) {
            manager.applyCompression(chest, player);
        }
        manager.saveInventory(chest);
        manager.save(player);
        messages.send(player, "upgrade-success", Map.of(
                "name", chest.getName(),
                "level", String.valueOf(level + 1)
        ));
        return true;
    }

    private UpgradeCost costForLevel(ChestUpgradeType type, int level) {
        UpgradeConfigEntry entry = plugin.upgrades().configFor(type);
        if (entry == null) {
            return UpgradeCost.free();
        }
        return entry.getCostForLevel(level);
    }

    private void appendCostLore(List<String> lore, UpgradeCost cost, Player player) {
        if (cost == null || cost.isFree()) {
            lore.add(messages.color("&7Cost: &aFree"));
            return;
        }
        lore.add(messages.color("&7Cost:"));
        if (cost.hasEconomy()) {
            lore.add(messages.color("&8 - &6" + economyFormat.format(cost.getEconomyAmount()) + " coins"));
        }
        if (cost.hasXp()) {
            lore.add(messages.color("&8 - &d" + cost.getXpLevels() + " xp levels"));
        }
        if (cost.hasItems()) {
            for (Map.Entry<Material, Integer> entry : cost.getItemCosts().entrySet()) {
                lore.add(messages.color("&8 - &b" + entry.getValue() + "x " + formatMaterial(entry.getKey())));
            }
        }
        if (cost.hasEconomy()) {
            EconomyBridge economy = plugin.economy();
            if (economy != null) {
                double balance = economy.balance(player);
                lore.add(messages.color("&7Balance: &6" + economyFormat.format(balance) + " coins"));
            } else {
                lore.add(messages.color("&7Balance: &cEconomy unavailable"));
            }
        }
    }

    private boolean processPayment(Player player, ChestUpgradeType type, UpgradeCost cost) {
        if (cost == null || cost.isFree()) {
            return true;
        }
        EconomyBridge economy = plugin.economy();
        if (cost.hasEconomy()) {
            if (economy == null) {
                messages.send(player, "upgrade-no-economy", null);
                return false;
            }
            if (!economy.has(player, cost.getEconomyAmount())) {
                sendInsufficientFundsMessage(player, type, cost.getEconomyAmount(), economy.balance(player));
                return false;
            }
        }
        if (cost.hasXp() && player.getLevel() < cost.getXpLevels()) {
            messages.send(player, "upgrade-no-xp", Map.of("levels", String.valueOf(cost.getXpLevels())));
            return false;
        }
        if (cost.hasItems() && !hasRequiredItems(player, cost.getItemCosts())) {
            String missing = formatMissingItems(player, cost.getItemCosts());
            messages.send(player, "upgrade-no-items", Map.of("items", missing));
            return false;
        }

        if (cost.hasEconomy()) {
            if (economy == null || !economy.withdraw(player, cost.getEconomyAmount())) {
                sendInsufficientFundsMessage(player, type, cost.getEconomyAmount(), economy.balance(player));
                return false;
            }
        }
        if (cost.hasXp()) {
            player.giveExpLevels(-cost.getXpLevels());
        }
        if (cost.hasItems()) {
            removeItems(player, cost.getItemCosts());
        }
        return true;
    }

    private boolean hasRequiredItems(Player player, Map<Material, Integer> items) {
        Map<Material, Integer> remaining = new HashMap<>(items);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            Material mat = stack.getType();
            if (!remaining.containsKey(mat)) continue;
            int needed = remaining.get(mat);
            int used = Math.min(needed, stack.getAmount());
            remaining.put(mat, needed - used);
        }
        return remaining.values().stream().allMatch(value -> value <= 0);
    }

    private String formatMissingItems(Player player, Map<Material, Integer> required) {
        Map<Material, Integer> remaining = new HashMap<>(required);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            Material mat = stack.getType();
            if (!remaining.containsKey(mat)) continue;
            int needed = remaining.get(mat);
            int used = Math.min(needed, stack.getAmount());
            remaining.put(mat, needed - used);
        }
        return remaining.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getValue() + "x " + formatMaterial(entry.getKey()))
                .collect(Collectors.joining(", "));
    }

    private void removeItems(Player player, Map<Material, Integer> items) {
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            int remaining = entry.getValue();
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType() != entry.getKey()) continue;
                int toRemove = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - toRemove);
                remaining -= toRemove;
                if (stack.getAmount() <= 0) {
                    contents[slot] = null;
                }
            }
            player.getInventory().setContents(contents);
        }
    }

    private void sendInsufficientFundsMessage(Player player, ChestUpgradeType type, double required, double balance) {
        String requiredFormatted = economyFormat.format(required);
        String balanceFormatted = economyFormat.format(balance);
        String upgradeDisplay = ChatColor.stripColor(upgradeName(type));
        List<String> hover = List.of(
                messages.color("&6Upgrade: &e" + upgradeDisplay),
                messages.color("&6Required: &e" + requiredFormatted + " coins"),
                messages.color("&6Balance: &e" + balanceFormatted + " coins")
        );
        messages.sendWithHover(player, "upgrade-no-funds", Map.of("amount", requiredFormatted), hover);
    }

    private String formatMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] parts = input.toLowerCase(Locale.ENGLISH).split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }

    private String translationKey(ChestUpgradeType type, String suffix) {
        return "upgrades." + type.getKey() + "." + suffix;
    }

    private String upgradeName(ChestUpgradeType type) {
        String key = translationKey(type, "name");
        if (messages.has(key)) {
            return messages.text(key);
        }
        return messages.color("&6" + type.getDisplayName());
    }

    private void addDescriptionLore(List<String> lore, ChestUpgradeType type) {
        String key = translationKey(type, "description");
        if (messages.has(key)) {
            lore.add(messages.text(key));
        }
    }
}
