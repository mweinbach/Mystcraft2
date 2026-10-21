/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Calls {@code onLoad} when a block entity joins a loaded chunk, queues it for the block entities of a chunk that has
 * just loaded, and calls {@code onChunkUnloaded} before a chunk's block entities are removed, where Forge does.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Inject(method = "addAndRegisterBlockEntity", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/chunk/LevelChunk;updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", shift = At.Shift.AFTER))
    private void mekanism$onLoad(BlockEntity blockEntity, CallbackInfo ci) {
        blockEntity.onLoad();
    }

    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("TAIL"))
    private void mekanism$queueOnLoad(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        self.getLevel().addFreshBlockEntities(self.getBlockEntities().values());
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void mekanism$onChunkUnloaded(CallbackInfo ci) {
        ((LevelChunk) (Object) this).getBlockEntities().values().forEach(BlockEntity::onChunkUnloaded);
    }
}
