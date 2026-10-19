package com.technovision.advancedgenetics.common.item;

import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CellItem extends Item {

    private final EntityType<?> entityType;
    private final int color;

    public CellItem(EntityType<?> entityType, int color) {
        super(ItemRegistry.properties());
        this.entityType = entityType;
        this.color = color;
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    public int getColor(ItemStack itemStack, int tintIndex) {
        return tintIndex > 0 ? -1 : color;
    }
}
