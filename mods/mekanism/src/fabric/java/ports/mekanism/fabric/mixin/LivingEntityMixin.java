/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.extensions.IForgeItemStack;

/**
 * Equipment slots, elytra flight and the fall-landing sound. Hurt, fall damage, attack and jump stay on vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    protected int fallFlyTicks;

    @Inject(method = "getEquipmentSlotForItem", at = @At("HEAD"), cancellable = true)
    private static void mekanism$equipmentSlot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
        EquipmentSlot slot = ((IForgeItemStack) (Object) stack).getEquipmentSlot();
        if (slot != null) {
            cir.setReturnValue(slot);
        }
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mekanism$anyElytra(ItemStack stack, Item item) {
        if (item != Items.ELYTRA) {
            return stack.is(item);
        }
        return true;
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ElytraItem;isFlyEnabled(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mekanism$elytraTick(ItemStack stack) {
        LivingEntity self = (LivingEntity) (Object) this;
        return ((IForgeItemStack) (Object) stack).canElytraFly(self) && ((IForgeItemStack) (Object) stack).elytraFlightTick(self, this.fallFlyTicks);
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"))
    private void mekanism$elytraDamageAlreadyApplied(ItemStack stack, int amount, LivingEntity entity, Consumer<LivingEntity> onBroken) {
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/LivingEntity;gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;)V"))
    private void mekanism$elytraGlideAlreadyEmitted(LivingEntity entity, GameEvent event) {
    }

    @Redirect(method = "playBlockFallSound", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType mekanism$fallSound(BlockState state) {
        LivingEntity self = (LivingEntity) (Object) this;
        BlockPos pos = new BlockPos(self.getX(), self.getY() - 0.2D, self.getZ());
        return state.getSoundType(self.level, pos, self);
    }
}
