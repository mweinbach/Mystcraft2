package net.mystcraft.fabric.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.minecraft.client.age.AgeVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class AgeSkyColorMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void mystcraft$sky(Vec3 camera, float partial, CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(AgeVisualClient.sky((ClientLevel) (Object) this, camera, partial, callback.getReturnValue()));
    }
    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void mystcraft$cloud(float partial, CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(AgeVisualClient.cloud((ClientLevel) (Object) this, partial, callback.getReturnValue()));
    }
}
