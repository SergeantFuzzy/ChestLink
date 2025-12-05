package dev.sergeantfuzzy.chestlink.upgrade;

import dev.sergeantfuzzy.chestlink.ChestUpgradeType;
import org.bukkit.Material;

/**
 * Base implementation providing standard metadata wiring for upgrade types.
 */
public abstract class AbstractChestUpgrade implements ChestUpgrade {
    private final ChestUpgradeType type;
    private final Material icon;
    private final String descriptionOverride;

    protected AbstractChestUpgrade(ChestUpgradeType type, Material icon) {
        this(type, icon, null);
    }

    protected AbstractChestUpgrade(ChestUpgradeType type, Material icon, String descriptionOverride) {
        this.type = type;
        this.icon = icon;
        this.descriptionOverride = descriptionOverride;
    }

    @Override
    public String getKey() {
        return type.getKey();
    }

    @Override
    public ChestUpgradeType getType() {
        return type;
    }

    @Override
    public String getDisplayName() {
        return type.getDisplayName();
    }

    @Override
    public String getDescription() {
        return descriptionOverride != null ? descriptionOverride : type.getDescription();
    }

    @Override
    public Material getIcon() {
        return icon;
    }
}
