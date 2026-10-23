/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.VertexBufferExt;
import ports.metalrender.terrain.TerrainLayer;

/**
 * Lets a chunk layer VertexBuffer remember its section and layer, and makes drawing a buffer that was
 * never uploaded a no-op. That happens for one frame when Metal turns off mid-frame (a command, the
 * failure fallback): chunks compiled for Metal have no OpenGL data until the queued rebuild runs.
 */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin implements VertexBufferExt {

    @Shadow
    private VertexFormat.Mode mode;

    @Unique
    private ChunkRenderDispatcher.RenderChunk metalrender$chunk;
    @Unique
    private TerrainLayer metalrender$layer;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void metalrender$skipNeverUploaded(final CallbackInfo ci) {
        if (mode == null) {
            ci.cancel();
        }
    }

    @Override
    public void metalrender$tag(final ChunkRenderDispatcher.RenderChunk chunk, final TerrainLayer layer) {
        this.metalrender$chunk = chunk;
        this.metalrender$layer = layer;
    }

    @Override
    public ChunkRenderDispatcher.RenderChunk metalrender$chunk() {
        return metalrender$chunk;
    }

    @Override
    public TerrainLayer metalrender$layer() {
        return metalrender$layer;
    }
}
