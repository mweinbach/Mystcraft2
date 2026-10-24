/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.item.BucketItem;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeBucketItem;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin implements IForgeBucketItem {
}
