/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.common.ToolActions;
import ports.projecte.lib.common.extensions.IForgeItem;

/**
 * Forge gates {@link ShovelItem#useOn} on {@link ToolActions#SHOVEL_FLATTEN}. The vanilla body still flattens blocks
 * and extinguishes campfires.
 */
@Mixin(ShovelItem.class)
public abstract class ShovelItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void projecte$requireFlatten(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = context.getItemInHand();
        if (!((IForgeItem) stack.getItem()).canPerformAction(stack, ToolActions.SHOVEL_FLATTEN)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
