package com.technovision.advancedgenetics.common.item;

import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public class OrganicMatterItem extends Item {

    private final EntityType<?> entityType;

    public OrganicMatterItem(EntityType<?> entityType) {
        super(ItemRegistry.properties());
        this.entityType = entityType;
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }
}
