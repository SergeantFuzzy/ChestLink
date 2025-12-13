package dev.sergeantfuzzy.chestlink.core.data;

public enum InventoryType {
    SINGLE(27, "Single Chest"),
    DOUBLE(54, "Double Chest");

    private final int size;
    private final String displayName;

    InventoryType(int size, String displayName) {
        this.size = size;
        this.displayName = displayName;
    }

    public int getSize() {
        return size;
    }

    public String getDisplayName() {
        return displayName;
    }
}