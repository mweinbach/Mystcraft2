/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.metalrender.client.MetalTerrainRenderer;

/** Adds MetalRender tier, upscaler and GPU culling counters to the F3 screen. */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void metalrender$gameInformation(final CallbackInfoReturnable<List<String>> cir) {
        cir.getReturnValue().addAll(MetalTerrainRenderer.debugLines());
    }
}
