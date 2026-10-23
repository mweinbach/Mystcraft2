/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.extremereactors.fabric.client.FabricClientPlatform;

/**
 * Fabric API 0.77 has no once-per-frame event: {@code WorldRenderEvents} fire per world render, and not at all
 * without a world. The game renders each frame with one call to this method, right where Forge ends its render tick.
 * The injection only observes the return, so it composes with other mods' mixins on the method.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void bigreactors$onFrameEnd(final float partialTicks, final long nanoTime, final boolean renderLevel,
                                        final CallbackInfo callback) {
        FabricClientPlatform.fireRenderFrameEnd();
    }
}
