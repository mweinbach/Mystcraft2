/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.mekanism.lib.common.extensions.IForgeItemStack;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Redirect(method = "tryToStartFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mekanism$anyElytra(ItemStack stack, Item item) {
        if (item == Items.ELYTRA) {
            return true;
        }
        return stack.is(item);
    }

    @Redirect(method = "tryToStartFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ElytraItem;isFlyEnabled(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mekanism$canElytraFly(ItemStack stack) {
        return ((IForgeItemStack) (Object) stack).canElytraFly((Player) (Object) this);
    }
}
