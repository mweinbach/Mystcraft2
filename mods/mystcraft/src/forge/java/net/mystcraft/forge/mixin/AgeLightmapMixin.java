package net.mystcraft.forge.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import net.mystcraft.minecraft.client.age.AgeVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public abstract class AgeLightmapMixin {
    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private static void mystcraft$brightness(DimensionType type, int light, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(AgeVisualClient.brightness(type, light, callback.getReturnValue()));
    }
}
