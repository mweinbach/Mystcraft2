/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.Layers;
import ports.metalrender.client.MetalTerrainRenderer;
import ports.metalrender.client.PerfStats;

/**
 * Replaces the solid and cutout chunk layer draws with the Metal opaque pass, committed before
 * vanilla's terrain setup and composited where vanilla would draw the solid layer.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Unique
    private long metalrender$layerStart;

    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void metalrender$renderChunkLayer(final RenderType type, final PoseStack poseStack, final double camX,
                                               final double camY, final double camZ, final Matrix4f projection,
                                               final CallbackInfo ci) {
        if (MetalTerrainRenderer.renderLayer(type, poseStack, camX, camY, camZ, projection)) {
            ci.cancel();
        } else {
            metalrender$layerStart = System.nanoTime();
        }
    }

    /** Commits the Metal opaque pass before vanilla's terrain setup, so the two overlap. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;"
            + "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V"))
    private void metalrender$beginTerrain(final PoseStack poseStack, final float partialTick, final long finishNanoTime,
                                          final boolean renderBlockOutline, final Camera camera,
                                          final GameRenderer gameRenderer, final LightTexture lightTexture,
                                          final Matrix4f projection, final CallbackInfo ci) {
        MetalTerrainRenderer.beginFrame(poseStack, camera, projection);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void metalrender$endFrame(final PoseStack poseStack, final float partialTick, final long finishNanoTime,
                                      final boolean renderBlockOutline, final Camera camera,
                                      final GameRenderer gameRenderer, final LightTexture lightTexture,
                                      final Matrix4f projection, final CallbackInfo ci) {
        MetalTerrainRenderer.endFrame();
    }

    /** Only reached when vanilla drew the layer itself. */
    @Inject(method = "renderChunkLayer", at = @At("RETURN"))
    private void metalrender$renderChunkLayerEnd(final RenderType type, final PoseStack poseStack, final double camX,
                                                  final double camY, final double camZ, final Matrix4f projection,
                                                  final CallbackInfo ci) {
        if (Layers.of(type) != null) {
            PerfStats.vanillaTerrainNanos += System.nanoTime() - metalrender$layerStart;
        }
    }

    @Inject(method = "addRecentlyCompiledChunk", at = @At("HEAD"))
    private void metalrender$chunkCompiled(final ChunkRenderDispatcher.RenderChunk chunk, final CallbackInfo ci) {
        MetalTerrainRenderer.queueEmptyLayerSync(chunk);
    }
}
