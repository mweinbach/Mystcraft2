/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.fabric.FabricLifecycle;
import ports.mekanism.lib.client.event.TextureStitchEvent;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

    @Inject(method = "reload(Lnet/minecraft/client/renderer/texture/TextureAtlas$Preparations;)V", at = @At("TAIL"))
    private void mekanism$stitched(TextureAtlas.Preparations preparations, CallbackInfo ci) {
        FabricLifecycle.postToMods(new TextureStitchEvent.Post((TextureAtlas) (Object) this));
    }
}
