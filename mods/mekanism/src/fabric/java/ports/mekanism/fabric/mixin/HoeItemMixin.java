/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.ToolActions;
import ports.mekanism.lib.common.extensions.IForgeItem;

/**
 * Forge gates {@link HoeItem#useOn} on {@link ToolActions#HOE_TILL}. The vanilla body still tills, so rooted dirt
 * drops hanging roots.
 */
@Mixin(HoeItem.class)
public abstract class HoeItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void mekanism$requireTill(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = context.getItemInHand();
        if (!((IForgeItem) stack.getItem()).canPerformAction(stack, ToolActions.HOE_TILL)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
