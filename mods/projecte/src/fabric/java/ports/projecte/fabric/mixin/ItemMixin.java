/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeItem;

/**
 * Gives every item the port's Forge item hooks. Fabric API's {@code FabricItem} methods are answered only on the
 * ProjectE classes that override the matching Forge hook (see {@link ItemPEMixin}, {@link ChargeAttributesMixin} and
 * {@link CraftingRemainderMixin}); merging them into {@link Item} itself would collide with other mods' shims.
 */
@Mixin(Item.class)
public abstract class ItemMixin implements IForgeItem {
}
