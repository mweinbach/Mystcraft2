package net.mystcraft.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.mystcraft.minecraft.client.DynamicPageRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runtime page textures do not outlive a client world/server session. */
@Mixin(Minecraft.class)
public abstract class DynamicPageDisconnectMixin {
    @Inject(method="clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",at=@At("HEAD"))
    private void mystcraft$clearDynamicPages(net.minecraft.client.gui.screens.Screen screen,CallbackInfo callback) {
        DynamicPageRenderer.clear();
    }
}
