/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.metalrender.client.ChunkUploads;
import ports.metalrender.client.MetalTerrainRenderer;

/** Routes chunk layer uploads to Metal; vanilla's render-thread upload queue applies them. */
@Mixin(ChunkRenderDispatcher.class)
public abstract class ChunkRenderDispatcherMixin {

    @Shadow
    @Final
    private Queue<Runnable> toUpload;

    @Inject(method = "uploadChunkLayer", at = @At("HEAD"), cancellable = true)
    private void metalrender$uploadChunkLayer(final BufferBuilder.RenderedBuffer buffer, final VertexBuffer target,
                                               final CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        final CompletableFuture<Void> upload = ChunkUploads.submit(buffer, target, toUpload::add);
        if (upload != null) {
            cir.setReturnValue(upload);
        }
    }

    @Inject(method = "uploadAllPendingUploads", at = @At("HEAD"))
    private void metalrender$applyPendingOps(final CallbackInfo ci) {
        MetalTerrainRenderer.drainPendingOps();
    }
}
