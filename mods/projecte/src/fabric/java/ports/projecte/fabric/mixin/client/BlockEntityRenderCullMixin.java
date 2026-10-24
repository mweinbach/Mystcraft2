/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.projecte.lib.common.extensions.IForgeBlockEntity;

/**
 * Forge skips a block entity renderer when its {@link IForgeBlockEntity#getRenderBoundingBox()} misses the frustum.
 * The two calls are the chunk's block entities and the global list of renderers that draw while their chunk is off
 * screen. Without the box, a multiblock or miner disappears once its master block leaves the view.
 */
@Mixin(LevelRenderer.class)
public abstract class BlockEntityRenderCullMixin {

    @WrapOperation(method = "renderLevel", require = 2, at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void projecte$cullByRenderBoundingBox(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float partialTick,
          PoseStack poseStack, MultiBufferSource buffer, Operation<Void> original, @Local Frustum frustum) {
        if (frustum.isVisible(((IForgeBlockEntity) blockEntity).getRenderBoundingBox())) {
            original.call(dispatcher, blockEntity, partialTick, poseStack, buffer);
        }
    }
}
