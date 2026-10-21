/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.item.BucketItem;
import org.spongepowered.asm.mixin.Mixin;
import ports.mekanism.lib.common.extensions.IForgeBucketItem;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin implements IForgeBucketItem {
}
