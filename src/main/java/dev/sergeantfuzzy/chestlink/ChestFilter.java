package dev.sergeantfuzzy.chestlink;

import org.bukkit.Material;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ChestFilter {
    private FilterMode mode;
    private final LinkedHashSet<Material> entries = new LinkedHashSet<>();

    public ChestFilter(FilterMode mode, Collection<Material> materials) {
        this.mode = mode == null ? FilterMode.WHITELIST : mode;
        if (materials != null) {
            materials.stream().filter(m -> m != null && m != Material.AIR).forEach(entries::add);
        }
    }

    public static ChestFilter empty(FilterMode mode) {
        return new ChestFilter(mode, null);
    }

    public FilterMode getMode() {
        return mode;
    }

    public void setMode(FilterMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    public boolean add(Material material, int maxEntries) {
        if (material == null || material == Material.AIR) {
            return false;
        }
        if (entries.contains(material)) {
            return false;
        }
        if (maxEntries > 0 && entries.size() >= maxEntries) {
            return false;
        }
        return entries.add(material);
    }

    public boolean remove(Material material) {
        if (material == null) {
            return false;
        }
        return entries.remove(material);
    }

    public boolean allows(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }
        boolean contains = entries.contains(material);
        return mode == FilterMode.WHITELIST ? contains : !contains;
    }

    public Set<Material> getEntries() {
        return Set.copyOf(entries);
    }

    public ChestFilter copy() {
        return new ChestFilter(mode, entries);
    }

    public List<String> serialize() {
        return entries.stream()
                .map(material -> material.name().toLowerCase())
                .collect(Collectors.toList());
    }

    public static ChestFilter fromSerialized(FilterMode mode, Collection<String> serialized) {
        if (serialized == null) {
            return new ChestFilter(mode, null);
        }
        List<Material> materials = serialized.stream()
                .map(value -> value == null ? null : Material.matchMaterial(value.toUpperCase()))
                .filter(m -> m != null && m != Material.AIR)
                .collect(Collectors.toList());
        return new ChestFilter(mode, materials);
    }
}
