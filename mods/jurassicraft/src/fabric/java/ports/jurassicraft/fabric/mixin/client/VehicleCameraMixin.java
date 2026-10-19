package ports.jurassicraft.fabric.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.jurassicraft.client.JurassicClient;

/** Fabric has no camera-angle event; apply zoom after vanilla finishes collision-aware camera setup. */
@Mixin(Camera.class)
public abstract class VehicleCameraMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void jurassicraft$zoom(BlockGetter world, Entity entity, boolean detached, boolean reverse,
                                  float partialTick, CallbackInfo callback) {
        JurassicClient.camera((Camera) (Object) this, partialTick);
    }
}
