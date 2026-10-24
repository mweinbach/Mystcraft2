/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.projecte.lib.common.extensions.IForgeItemStack;

@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {

    @Redirect(method = "canEntityWalkOnPowderSnow", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private static boolean projecte$walk(ItemStack stack, Item item, Entity entity) {
        if (item == Items.LEATHER_BOOTS && entity instanceof LivingEntity living) {
            return ((IForgeItemStack) (Object) stack).canWalkOnPowderedSnow(living);
        }
        return stack.is(item);
    }
}
