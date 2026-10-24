/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PumpkinBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.projecte.lib.common.ToolActions;
import ports.projecte.lib.common.extensions.IForgeItem;

/**
 * Forge lets any item that can {@link ToolActions#SHEARS_CARVE} carve a pumpkin. The vanilla body still carves it.
 */
@Mixin(PumpkinBlock.class)
public abstract class PumpkinBlockMixin {

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean projecte$shearsCarve(ItemStack stack, Item item) {
        return stack.is(item) || ((IForgeItem) stack.getItem()).canPerformAction(stack, ToolActions.SHEARS_CARVE);
    }
}
