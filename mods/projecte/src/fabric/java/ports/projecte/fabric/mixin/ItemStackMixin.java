/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.fabric.PEHooks;
import ports.projecte.lib.common.capabilities.AttachedCapabilities;
import ports.projecte.lib.common.extensions.IForgeItem;
import ports.projecte.lib.common.extensions.IForgeItemStack;

/**
 * Gives item stacks the capabilities of their item, and routes max damage and tooltip hide flags through the item
 * hooks Forge calls from {@code ItemStack}.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements IForgeItemStack, AttachedCapabilities.Holder {

    @Unique
    @Nullable
    private AttachedCapabilities projecte$capabilities;

    @Override
    public AttachedCapabilities projecte$capabilities() {
        if (projecte$capabilities == null) {
            ItemStack self = (ItemStack) (Object) this;
            projecte$capabilities = AttachedCapabilities.of(self.getItem().initCapabilities(self, null));
        }
        return projecte$capabilities;
    }

    @WrapOperation(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z"))
    private <T extends LivingEntity> boolean projecte$damageItem(ItemStack stack, int amount, RandomSource random, @Nullable ServerPlayer player,
          Operation<Boolean> original, @Local(ordinal = 0) T entity, @Local(ordinal = 0) Consumer<T> onBroken) {
        // The generic signature of hurtAndBreak makes ModifyVariable see an extra int, so the damage hook wraps hurt.
        int modified = ((IForgeItem) stack.getItem()).projecte$damageItem(stack, amount, entity, onBroken);
        return original.call(stack, modified, random, player);
    }

    /**
     * Forge's {@code ItemStack.isCorrectToolForDrops} asks the stack-aware item method. Only ProjectE's items answer, so
     * another Forge-API layer's identical hook (Mekanism's, Porting Lib's) still decides for its own items.
     */
    @Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
    private void projecte$correctTool(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!PEHooks.ours(self.getItem())) {
            return;
        }
        cir.setReturnValue(((IForgeItem) self.getItem()).projecte$isCorrectToolForDrops(self, state));
    }

    @Inject(method = "getMaxDamage", at = @At("HEAD"), cancellable = true)
    private void projecte$maxDamage(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!PEHooks.ours(self.getItem())) {
            return;
        }
        cir.setReturnValue(((IForgeItem) self.getItem()).projecte$getMaxDamage(self));
    }

    @Inject(method = "getHideFlags", at = @At("HEAD"), cancellable = true)
    private void projecte$hideFlags(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        CompoundTag tag = self.getTag();
        if (!PEHooks.ours(self.getItem()) || tag != null && tag.contains("HideFlags", 99)) {
            return;
        }
        cir.setReturnValue(((IForgeItem) self.getItem()).projecte$getDefaultTooltipHideFlags(self));
    }
}
