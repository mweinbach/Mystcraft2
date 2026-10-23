/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.gametest.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.gametest.MetalRenderBenchmark;

/** Benchmark only: times the buffer swap, so frame time can be split into CPU work and waiting. */
@Mixin(Window.class)
public abstract class WindowMixin {

    @Unique
    private long metalrender$swapStart;

    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void metalrender$swapStart(final CallbackInfo ci) {
        metalrender$swapStart = System.nanoTime();
    }

    @Inject(method = "updateDisplay", at = @At("RETURN"))
    private void metalrender$swapEnd(final CallbackInfo ci) {
        MetalRenderBenchmark.swapNanos += System.nanoTime() - metalrender$swapStart;
    }
}
