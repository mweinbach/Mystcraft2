package ports.gridwright.fabric.mixin;

import ports.gridwright.storage.Wards;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric has no teleport event, so Ender Wards stop endermen here, where Forge fires
 * {@code EntityTeleportEvent.EnderEntity}. Random and targeted teleports both end in this method.
 */
@Mixin(EnderMan.class)
abstract class EnderManMixin {

    @Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void gridwright$ward(double x, double y, double z, CallbackInfoReturnable<Boolean> info) {
        EnderMan self = (EnderMan) (Object) this;
        if (Wards.deniesTeleport(self.level, self, x, y, z)) info.setReturnValue(false);
    }
}
