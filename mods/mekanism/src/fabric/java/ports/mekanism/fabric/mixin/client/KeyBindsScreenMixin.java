/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.client.extensions.IForgeKeyMapping;
import ports.mekanism.lib.client.settings.KeyModifier;

/**
 * Records the modifier held while a key is bound, and leaves the binding selected when that press is itself a modifier
 * so Shift then N can be entered. A mouse button keeps the modifier already stored.
 */
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin {

    @Shadow
    private KeyMapping selectedKey;

    @Shadow
    private long lastKeySelection;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mekanism$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (selectedKey == null) {
            return;
        }
        InputConstants.Key pressed = keyCode == 256 ? InputConstants.UNKNOWN : InputConstants.getKey(keyCode, scanCode);
        ((IForgeKeyMapping) selectedKey).setKeyModifierAndCode(KeyModifier.getActiveModifier(), pressed);
        Minecraft.getInstance().options.setKey(selectedKey, pressed);
        if (!KeyModifier.isKeyCodeModifier(((IForgeKeyMapping) selectedKey).getKey())) {
            selectedKey = null;
        }
        lastKeySelection = Util.getMillis();
        KeyMapping.resetMapping();
        cir.setReturnValue(true);
    }

    /**
     * Reset All calls {@link KeyMapping#setKey} with the default key. That leaves a modifier in place. Forge replaces
     * the call with {@link IForgeKeyMapping#setToDefault()}.
     */
    @Redirect(method = "method_38532", remap = false, at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/KeyMapping;setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V", remap = true))
    private void mekanism$resetAll(KeyMapping mapping, InputConstants.Key key) {
        ((IForgeKeyMapping) mapping).setToDefault();
    }
}
