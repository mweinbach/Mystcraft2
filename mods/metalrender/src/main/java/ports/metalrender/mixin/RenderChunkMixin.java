/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.Layers;
import ports.metalrender.client.MetalTerrainRenderer;
import ports.metalrender.client.RenderChunkExt;
import ports.metalrender.client.VertexBufferExt;
import ports.metalrender.terrain.SectionSlot;
import ports.metalrender.terrain.TerrainLayer;

/** Tags the section's layer buffers and frees its Metal data whenever vanilla resets the section. */
@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public abstract class RenderChunkMixin implements RenderChunkExt {

    @Shadow
    @Final
    private Map<RenderType, VertexBuffer> buffers;

    @Unique
    private volatile int metalrender$generation;
    @Unique
    private volatile boolean metalrender$released;
    @Unique
    private SectionSlot metalrender$slot;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void metalrender$tagBuffers(final CallbackInfo ci) {
        final ChunkRenderDispatcher.RenderChunk self = (ChunkRenderDispatcher.RenderChunk) (Object) this;
        buffers.forEach((type, buffer) -> {
            final TerrainLayer layer = Layers.of(type);
            if (layer != null) {
                ((VertexBufferExt) buffer).metalrender$tag(self, layer);
            }
        });
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void metalrender$reset(final CallbackInfo ci) {
        metalrender$generation++;
        MetalTerrainRenderer.releaseSection(this);
    }

    @Inject(method = "releaseBuffers", at = @At("HEAD"))
    private void metalrender$releaseBuffers(final CallbackInfo ci) {
        metalrender$released = true;
    }

    @Override
    public int metalrender$generation() {
        return metalrender$generation;
    }

    @Override
    public boolean metalrender$released() {
        return metalrender$released;
    }

    @Override
    public SectionSlot metalrender$slot() {
        return metalrender$slot;
    }

    @Override
    public void metalrender$setSlot(final SectionSlot slot) {
        this.metalrender$slot = slot;
    }
}
