/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import ports.projecte.fabric.PEHooks;
import ports.projecte.lib.common.extensions.IForgeFriendlyByteBuf;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.At;
import ports.projecte.lib.common.extensions.IForgeItem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin implements IForgeFriendlyByteBuf {

    /** Forge sends the item's share tag rather than the full stack tag. */
    @WrapOperation(method = "writeItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getTag()Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag projecte$shareTag(ItemStack stack, Operation<CompoundTag> original) {
        return PEHooks.ours(stack.getItem()) ? ((IForgeItem) stack.getItem()).projecte$getShareTag(stack) : original.call(stack);
    }
}
