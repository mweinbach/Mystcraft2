package ports.gridwright.fabric.mixin;

import ports.gridwright.storage.Wards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric has no spawn-check event, so Spawn Wards refuse natural spawns here, the vanilla check Forge
 * wraps with {@code LivingSpawnEvent.CheckSpawn}. Only the natural spawn cycle calls this method.
 */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {

    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void gridwright$ward(ServerLevel level, Mob mob, double distance, CallbackInfoReturnable<Boolean> info) {
        if (Wards.deniesNaturalSpawn(level, mob)) info.setReturnValue(false);
    }
}
