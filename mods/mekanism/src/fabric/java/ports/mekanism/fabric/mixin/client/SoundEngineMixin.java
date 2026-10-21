/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.fabric.FabricLifecycle;
import ports.mekanism.lib.client.ForgeHooksClient;
import ports.mekanism.lib.client.event.sound.SoundEngineLoadEvent;

/**
 * Fires Forge's sound engine load event and lets the play sound event replace or cancel a sound before it plays.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Shadow
    private boolean loaded;

    @Unique
    private boolean mekanism$playingReplacement;

    @Shadow
    public abstract void play(SoundInstance soundInstance);

    @Inject(method = "loadLibrary", at = @At("TAIL"))
    private void mekanism$loaded(CallbackInfo ci) {
        if (loaded) {
            FabricLifecycle.postToMods(new SoundEngineLoadEvent((SoundEngine) (Object) this));
        }
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void mekanism$playSound(SoundInstance sound, CallbackInfo ci) {
        if (!loaded || mekanism$playingReplacement) {
            return;
        }
        SoundInstance replacement = ForgeHooksClient.playSound((SoundEngine) (Object) this, sound);
        if (replacement != sound) {
            ci.cancel();
            if (replacement != null) {
                mekanism$playingReplacement = true;
                try {
                    play(replacement);
                } finally {
                    mekanism$playingReplacement = false;
                }
            }
        }
    }
}
