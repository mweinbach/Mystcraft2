/*
 * ProjectE 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.client.event.ComputeFovModifierEvent;
import ports.projecte.lib.common.MinecraftForge;

/** Posts Forge's {@code ComputeFovModifierEvent} with the modifier vanilla computed. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void projecte$fov(CallbackInfoReturnable<Float> cir) {
        ComputeFovModifierEvent event = new ComputeFovModifierEvent((AbstractClientPlayer) (Object) this, cir.getReturnValueF());
        MinecraftForge.EVENT_BUS.post(event);
        cir.setReturnValue(event.getNewFovModifier());
    }
}
