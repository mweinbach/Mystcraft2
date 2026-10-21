/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.ToolActions;
import ports.mekanism.lib.common.extensions.IForgeItem;

/**
 * Forge gates {@link AxeItem#useOn} on the axe strip, scrape and wax-off actions. The vanilla body still strips,
 * scrapes and dewaxes, including any block the item is allowed to modify.
 */
@Mixin(AxeItem.class)
public abstract class AxeItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void mekanism$requireAxeAction(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = context.getItemInHand();
        IForgeItem item = (IForgeItem) stack.getItem();
        if (!item.canPerformAction(stack, ToolActions.AXE_STRIP)
              && !item.canPerformAction(stack, ToolActions.AXE_SCRAPE)
              && !item.canPerformAction(stack, ToolActions.AXE_WAX_OFF)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
