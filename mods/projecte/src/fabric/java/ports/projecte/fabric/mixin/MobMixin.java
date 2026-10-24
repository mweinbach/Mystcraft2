/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.lib.event.ForgeEventFactory;

/**
 * Forge posts {@code LivingSpawnEvent.SpecialSpawn} just before {@code finalizeSpawn}. Posting at the head of that
 * method puts Mekanism armor on the mob before vanilla fills the empty slots. A canceled event skips this method's
 * body. A subclass that keeps going after {@code super.finalizeSpawn} still runs.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void projecte$specialSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData spawnData,
          CompoundTag dataTag, CallbackInfoReturnable<SpawnGroupData> cir) {
        Mob mob = (Mob) (Object) this;
        if (ForgeEventFactory.doSpecialSpawn(mob, level, (float) mob.getX(), (float) mob.getY(), (float) mob.getZ(), null, reason)) {
            cir.setReturnValue(spawnData);
        }
    }
}
