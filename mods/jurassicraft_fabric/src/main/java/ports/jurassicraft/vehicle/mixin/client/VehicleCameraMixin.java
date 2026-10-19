package ports.jurassicraft.vehicle.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.jurassicraft.vehicle.client.VehicleCamera;

/** Apply optional zoom after vanilla finishes collision-aware camera setup. */
@Mixin(Camera.class)
public abstract class VehicleCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Inject(method = "setup", at = @At("TAIL"))
    private void jurassicraft$zoom(BlockGetter world, Entity entity, boolean detached, boolean reverse,
                                  float partialTick, CallbackInfo callback) {
        VehicleCamera.apply((Camera)(Object)this, partialTick, pos -> setPosition(pos.x, pos.y, pos.z));
    }
}
