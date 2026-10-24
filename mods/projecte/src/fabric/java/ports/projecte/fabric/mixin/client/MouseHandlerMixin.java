/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.ForgeHooksClient;

/**
 * Posts the scroll event at the point Forge does: in game, with no screen, after a whole notch has accumulated and
 * before the hotbar or the spectator menu moves. Canceling it consumes the notch without scrolling.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    /**
     * Forge treats a horizontal wheel with no vertical movement as vertical on macOS, before the notch is accumulated.
     * {@code vertical} is the second double argument.
     */
    @ModifyVariable(method = "onScroll", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double projecte$osxVertical(double vertical, long window, double horizontal) {
        if (Minecraft.ON_OSX && vertical == 0.0D) {
            return horizontal;
        }
        return vertical;
    }

    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), cancellable = true)
    private void projecte$mouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()) {
            return;
        }
        double offset = vertical;
        if (Minecraft.ON_OSX && vertical == 0.0D) {
            offset = horizontal;
        }
        double delta = (Boolean.TRUE.equals(minecraft.options.discreteMouseScroll().get()) ? Math.signum(offset) : offset)
              * minecraft.options.mouseWheelSensitivity().get();
        if (ForgeHooksClient.onMouseScroll((MouseHandler) (Object) this, delta)) {
            ci.cancel();
        }
    }
}
