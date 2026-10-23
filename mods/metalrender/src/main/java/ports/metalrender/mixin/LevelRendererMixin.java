/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.MetalTerrainRenderer;

/** Replaces the solid and cutout chunk layer draws with the Metal opaque pass. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void metalrender$renderChunkLayer(final RenderType type, final PoseStack poseStack, final double camX,
                                               final double camY, final double camZ, final Matrix4f projection,
                                               final CallbackInfo ci) {
        if (MetalTerrainRenderer.renderLayer(type, poseStack, camX, camY, camZ, projection)) {
            ci.cancel();
        }
    }

    @Inject(method = "addRecentlyCompiledChunk", at = @At("HEAD"))
    private void metalrender$chunkCompiled(final ChunkRenderDispatcher.RenderChunk chunk, final CallbackInfo ci) {
        MetalTerrainRenderer.queueEmptyLayerSync(chunk);
    }
}
