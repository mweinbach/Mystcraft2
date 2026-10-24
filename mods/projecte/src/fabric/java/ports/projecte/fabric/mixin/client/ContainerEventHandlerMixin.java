/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.client.extensions.IForgeKeyMapping;
import ports.projecte.lib.client.settings.KeyModifier;

/**
 * Pressing Shift leaves the controls row selected so the next key can complete a chord. {@code KeyBindsScreen} does not
 * override {@code keyReleased}, so releasing that Shift without another key is handled on the container default, and
 * finishes the binding as Shift itself.
 */
@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {

    @Inject(method = "keyReleased(III)Z", at = @At("HEAD"), cancellable = true)
    private void projecte$finishModifierBinding(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof KeyBindsScreen screen) || screen.selectedKey == null) {
            return;
        }
        InputConstants.Key released = InputConstants.getKey(keyCode, scanCode);
        if (KeyModifier.isKeyCodeModifier(released) && released.equals(((IForgeKeyMapping) screen.selectedKey).getKey())) {
            screen.selectedKey = null;
            screen.lastKeySelection = Util.getMillis();
            KeyMapping.resetMapping();
            cir.setReturnValue(true);
        }
    }
}
