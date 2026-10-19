package com.technovision.advancedgenetics.common.item;

import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.world.item.Item;

public class DragonHealthCrystalItem extends Item {

    public DragonHealthCrystalItem() {
        super(ItemRegistry.properties().stacksTo(1).durability(1000));
    }
}
