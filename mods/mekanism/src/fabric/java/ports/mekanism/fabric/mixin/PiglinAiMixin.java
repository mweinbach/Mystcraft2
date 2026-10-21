/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.extensions.IForgeItem;

@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {

    @Inject(method = "isWearingGold", at = @At("HEAD"), cancellable = true)
    private static void mekanism$neutral(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        for (ItemStack stack : entity.getArmorSlots()) {
            if (((IForgeItem) stack.getItem()).makesPiglinsNeutral(stack, entity)) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
