package net.mystcraft.forge.mixin;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.mystcraft.minecraft.client.age.AgeVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionSpecialEffects.class)
public abstract class AgeCloudHeightMixin {
    @Inject(method = "getCloudHeight", at = @At("RETURN"), cancellable = true)
    private void mystcraft$height(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(AgeVisualClient.cloudHeight(callback.getReturnValue()));
    }
}
