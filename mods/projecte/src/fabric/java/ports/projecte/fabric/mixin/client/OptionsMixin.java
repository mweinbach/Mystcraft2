/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.extensions.IForgeKeyMapping;
import ports.projecte.lib.client.settings.KeyConflictContext;
import ports.projecte.lib.client.settings.KeyModifier;
import ports.projecte.lib.client.settings.KeyRebind;

/**
 * Saves a key mapping's modifier after its key name, and gives Forge's in-game conflict context to the vanilla keys
 * Forge 43.5.0 marks that way.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {

    @Shadow
    private KeyMapping keyUp;

    @Shadow
    private KeyMapping keyLeft;

    @Shadow
    private KeyMapping keyDown;

    @Shadow
    private KeyMapping keyRight;

    @Shadow
    private KeyMapping keyJump;

    @Shadow
    private KeyMapping keyShift;

    @Shadow
    private KeyMapping keySprint;

    @Shadow
    private KeyMapping keyAttack;

    @Shadow
    private KeyMapping keyChat;

    @Shadow
    private KeyMapping keyPlayerList;

    @Shadow
    private KeyMapping keyCommand;

    @Shadow
    private KeyMapping keyTogglePerspective;

    @Shadow
    private KeyMapping keySmoothCamera;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void projecte$inGameKeys(Minecraft minecraft, File gameDirectory, CallbackInfo ci) {
        inGame(keyUp);
        inGame(keyLeft);
        inGame(keyDown);
        inGame(keyRight);
        inGame(keyJump);
        inGame(keyShift);
        inGame(keySprint);
        inGame(keyAttack);
        inGame(keyChat);
        inGame(keyPlayerList);
        inGame(keyCommand);
        inGame(keyTogglePerspective);
        inGame(keySmoothCamera);
    }

    @Redirect(method = "processOptions", at = @At(value = "INVOKE",
          target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;"))
    private InputConstants.Key projecte$stripSavedModifier(String name) {
        int colon = name.indexOf(':');
        if (colon == -1) {
            KeyRebind.LOADED_MODIFIER.set(new KeyRebind.Loaded(KeyModifier.NONE, false));
            return InputConstants.getKey(name);
        }
        KeyRebind.LOADED_MODIFIER.set(new KeyRebind.Loaded(KeyModifier.valueFromString(name.substring(colon + 1)), true));
        return InputConstants.getKey(name.substring(0, colon));
    }

    @Redirect(method = "processOptions", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/KeyMapping;setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
    private void projecte$loadModifier(KeyMapping mapping, InputConstants.Key key) {
        KeyRebind.Loaded loaded = KeyRebind.LOADED_MODIFIER.get();
        KeyRebind.LOADED_MODIFIER.remove();
        if (loaded == null) {
            mapping.setKey(key);
            return;
        }
        KeyModifier modifier = loaded.modifier();
        // An options file written before modifiers were saved stores the bare key. When that key is still the
        // default, keep the mapping's default modifier instead of clearing it.
        if (!loaded.explicit() && modifier == KeyModifier.NONE && key.equals(mapping.getDefaultKey())) {
            modifier = ((IForgeKeyMapping) mapping).getDefaultKeyModifier();
        }
        ((IForgeKeyMapping) mapping).setKeyModifierAndCode(modifier, key);
    }

    @Unique
    private static void inGame(KeyMapping mapping) {
        ((IForgeKeyMapping) mapping).setKeyConflictContext(KeyConflictContext.IN_GAME);
    }
}
