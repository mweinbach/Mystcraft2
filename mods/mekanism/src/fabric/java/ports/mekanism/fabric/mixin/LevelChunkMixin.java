/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Calls {@code mekanism$onLoad} when a block entity joins a loaded chunk, queues it for the block entities of a chunk that has
 * just loaded, calls {@code onChunkUnloaded} before a chunk's block entities are removed, and hands chunk packet tags
 * to {@code handleUpdateTag}, where Forge does.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Inject(method = "addAndRegisterBlockEntity", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/chunk/LevelChunk;updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", shift = At.Shift.AFTER))
    private void mekanism$onLoad(BlockEntity blockEntity, CallbackInfo ci) {
        blockEntity.mekanism$onLoad();
    }

    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("TAIL"))
    private void mekanism$queueOnLoad(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        self.getLevel().mekanism$addFreshBlockEntities(self.getBlockEntities().values());
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void mekanism$onChunkUnloaded(CallbackInfo ci) {
        ((LevelChunk) (Object) this).getBlockEntities().values().forEach(BlockEntity::onChunkUnloaded);
    }

    /**
     * The block entity tags of a chunk packet are update tags; the lambda in {@code replaceWithPacketData} loads them.
     */
    @WrapOperation(method = "method_31716", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;load(Lnet/minecraft/nbt/CompoundTag;)V"))
    private void mekanism$handleUpdateTag(BlockEntity blockEntity, CompoundTag tag, Operation<Void> load) {
        blockEntity.handleUpdateTag(tag);
    }

    @Redirect(method = "method_12217", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"))
    private int mekanism$lightSource(BlockState state, BlockPos pos) {
        LevelChunk self = (LevelChunk) (Object) this;
        return state.getLightEmission(self.getLevel(), pos);
    }
}
