/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.projecte.lib.common.extensions.IForgeItem;

@Mixin(EnderMan.class)
public abstract class EnderManMixin {

    @Redirect(method = "isLookingAtMe", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean projecte$enderMask(ItemStack stack, Item item, Player player) {
        if (item == Blocks.CARVED_PUMPKIN.asItem()) {
            return ((IForgeItem) stack.getItem()).isEnderMask(stack, player, (EnderMan) (Object) this);
        }
        return stack.is(item);
    }
}
