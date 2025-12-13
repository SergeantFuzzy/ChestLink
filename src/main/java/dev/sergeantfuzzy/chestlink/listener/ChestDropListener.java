package dev.sergeantfuzzy.chestlink.listener;

import dev.sergeantfuzzy.chestlink.BoundChest;
import dev.sergeantfuzzy.chestlink.ChestDropSettings;
import dev.sergeantfuzzy.chestlink.ChestDropSettings.OverflowBehavior;
import dev.sergeantfuzzy.chestlink.ChestLinkManager;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import dev.sergeantfuzzy.chestlink.compat.SchedulerCompat;
import dev.sergeantfuzzy.chestlink.lang.MessageService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChestDropListener implements Listener {
    private static final long COOLDOWN_MS = 150L;

    private final ChestLinkPlugin plugin;
    private final ChestLinkManager manager;
    private final MessageService messages;
    private final ChestDropSettings settings;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Entity> activeHolograms = new ConcurrentHashMap<>();

    public ChestDropListener(ChestLinkPlugin plugin, ChestLinkManager manager, MessageService messages, ChestDropSettings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!settings.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        BoundChest chest = findOwnedChest(player);
        if (chest == null || chest.getLocation() == null) {
            return;
        }
        if (isOnCooldown(player.getUniqueId(), chest.getLocation())) {
            return;
        }
        updateCooldown(player.getUniqueId(), chest.getLocation());
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped == null || dropped.getType() == Material.AIR) {
            return;
        }
        Inventory targetInv = chest.getInventory();
        if (targetInv == null) {
            return;
        }

        if (settings.overflowBehavior() == OverflowBehavior.DENY && !canFullyStore(targetInv, dropped)) {
            event.setCancelled(true);
            restoreToPlayer(player, dropped);
            showHologram(chest.getLocation(), messages.getPrefix() + ChatColor.RED + "Linked chest is full", ChatColor.YELLOW + "Transfer cancelled.");
            updateCooldown(player.getUniqueId(), chest.getLocation());
            return;
        }

        // Let the drop action remove the item from the player, then intercept the entity
        event.setCancelled(false);
        ItemStack droppedStack = event.getItemDrop().getItemStack();
        int movedAmount = droppedStack.getAmount();
        event.getItemDrop().remove();

        ItemStack toStore = dropped.clone();
        toStore.setAmount(movedAmount);
        Map<Integer, ItemStack> leftover = targetInv.addItem(toStore);
        int moved = toStore.getAmount() - sumAmounts(leftover);
        boolean changed = moved > 0;
        if (moved > 0) {
            chest.markModified();
            handleSuccess(player, chest, toStore, moved);
        }
        handleOverflow(player, leftover, event.getItemDrop().getLocation());
        boolean filtered = manager.enforceFilter(chest, player);
        if (filtered) {
            chest.markModified();
            changed = true;
        }
        boolean compressed = manager.applyCompression(chest, player);
        if (compressed) {
            changed = true;
        }
        if (changed) {
            manager.saveInventory(chest);
            manager.scheduleAutoSort(chest);
        }
    }

    private BoundChest findOwnedChest(Player player) {
        Location base = player.getLocation();
        BlockFace facing = player.getFacing();
        Block front = base.getBlock().getRelative(facing);
        Block left = front.getRelative(rotateLeft(facing));
        Block right = front.getRelative(rotateRight(facing));
        BoundChest chest = ownedChestAt(player, front);
        if (chest != null) return chest;
        chest = ownedChestAt(player, left);
        if (chest != null) return chest;
        return ownedChestAt(player, right);
    }

    private BoundChest ownedChestAt(Player player, Block block) {
        if (block == null) return null;
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return null;
        }
        BoundChest chest = manager.getChest(player, block.getLocation());
        if (chest == null) {
            return null;
        }
        return chest.getOwner().equals(player.getUniqueId()) ? chest : null;
    }

    private boolean isOnCooldown(UUID playerId, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return true;
        }
        String key = playerId + ":" + loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < COOLDOWN_MS) {
            return true;
        }
        return false;
    }

    private void updateCooldown(UUID playerId, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String key = playerId + ":" + loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        cooldowns.put(key, System.currentTimeMillis());
    }

    private boolean canFullyStore(Inventory inv, ItemStack stack) {
        int remaining = stack.getAmount();
        int maxStack = stack.getMaxStackSize();
        ItemStack[] contents = inv.getContents();
        for (ItemStack slot : contents) {
            if (slot == null || slot.getType() == Material.AIR) {
                remaining -= Math.min(remaining, maxStack);
            } else if (slot.isSimilar(stack)) {
                int space = maxStack - slot.getAmount();
                if (space > 0) {
                    remaining -= Math.min(space, remaining);
                }
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private void handleSuccess(Player player, BoundChest chest, ItemStack stack, int moved) {
        if (settings.soundEnabled()) {
            try {
                Sound sound = Sound.valueOf(settings.depositSound());
                if (player.getWorld() != null) {
                    Location loc = chest.getLocation() != null ? chest.getLocation().clone().add(0.5, 0.5, 0.5) : player.getLocation();
                    player.getWorld().playSound(loc, sound, 0.6f, 1.1f);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (settings.hologramEnabled()) {
            String itemName = formatItemName(stack);
            String line1 = messages.getPrefix() + ChatColor.GREEN + "Transferred items to Linked Chest!";
            String line2 = ChatColor.GOLD + "+ " + moved + ChatColor.YELLOW + " Items Moved";
            String line3 = ChatColor.YELLOW + itemName + ChatColor.GOLD + " x" + moved;
            showHologram(chest.getLocation(), line1, line2 + "\n" + line3);
        }
    }

    private void handleOverflow(Player player, Map<Integer, ItemStack> leftover, Location dropLocation) {
        if (leftover == null || leftover.isEmpty()) {
            return;
        }
        if (settings.overflowBehavior() == OverflowBehavior.DROP) {
            Location target = dropLocation != null ? dropLocation : player.getLocation();
            for (ItemStack item : leftover.values()) {
                if (item == null || item.getType() == Material.AIR) continue;
                player.getWorld().dropItemNaturally(target, item);
            }
        } else if (settings.overflowBehavior() == OverflowBehavior.DENY) {
            for (ItemStack item : leftover.values()) {
                if (item == null) continue;
                player.getInventory().addItem(item);
            }
        }
    }

    private void restoreToPlayer(Player player, ItemStack stack) {
        if (stack == null) return;
        if (!player.getInventory().containsAtLeast(stack, stack.getAmount())) {
            player.getInventory().addItem(stack.clone());
        }
    }

    private int sumAmounts(Map<Integer, ItemStack> items) {
        int total = 0;
        for (ItemStack item : items.values()) {
            if (item != null) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private String formatItemName(ItemStack stack) {
        String name = stack.getType().name().toLowerCase().replace("_", " ");
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private void showHologram(Location chestLoc, String firstLine, String remainingLines) {
        if (chestLoc == null || chestLoc.getWorld() == null) return;
        String text = firstLine + "\n" + remainingLines;
        Location loc = chestLoc.clone().add(0.5, settings.hologramOffsetY(), 0.5);
        String key = hologramKey(chestLoc);
        removeExistingHologram(key);
        try {
            Component comp = LegacyComponentSerializer.legacySection().deserialize(text);
            String legacy = LegacyComponentSerializer.legacySection().serialize(comp);
            TextDisplay display = chestLoc.getWorld().spawn(loc, TextDisplay.class, spawned -> {
                spawned.setText(legacy);
                spawned.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                spawned.setSeeThrough(false);
                spawned.setShadowed(false);
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            long ticks = Math.max(1, Math.round(settings.hologramDurationSeconds() * 20));
            activeHolograms.put(key, display);
            SchedulerCompat.runLocationTaskLater(plugin, chestLoc, () -> removeIfSame(key, display), ticks);
        } catch (Exception ignored) {
            ArmorStand stand = chestLoc.getWorld().spawn(loc, ArmorStand.class, spawned -> {
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
            long ticks = Math.max(1, Math.round(settings.hologramDurationSeconds() * 20));
            activeHolograms.put(key, stand);
            SchedulerCompat.runLocationTaskLater(plugin, chestLoc, () -> removeIfSame(key, stand), ticks);
        }
    }

    private void removeExistingHologram(String key) {
        Entity previous = activeHolograms.remove(key);
        if (previous != null && !previous.isDead()) {
            previous.remove();
        }
    }

    private void removeIfSame(String key, Entity entity) {
        Entity current = activeHolograms.get(key);
        if (current != null && current.equals(entity)) {
            activeHolograms.remove(key);
            if (!entity.isDead()) {
                entity.remove();
            }
        } else if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    private String hologramKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private BlockFace rotateLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private BlockFace rotateRight(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }
}
