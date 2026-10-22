/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.ForgeHooksClient;

/**
 * Hands block entity data packets to {@code onDataPacket}, as Forge does. The lambda in
 * {@code handleBlockEntityData} loads the packet's tag. Recipe sync posts {@code RecipesUpdatedEvent} after the
 * client recipe book is rebuilt, which is where Forge posts it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @WrapOperation(method = "method_38542", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;load(Lnet/minecraft/nbt/CompoundTag;)V"))
    private void mekanism$onDataPacket(BlockEntity blockEntity, CompoundTag tag, Operation<Void> load, @Local(argsOnly = true) ClientboundBlockEntityDataPacket packet) {
        blockEntity.onDataPacket(((ClientPacketListener) (Object) this).getConnection(), packet);
    }

    @Inject(method = "handleUpdateRecipes", at = @At("RETURN"))
    private void mekanism$recipesUpdated(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        ForgeHooksClient.onRecipesUpdated(((ClientPacketListener) (Object) this).getRecipeManager());
    }
}
