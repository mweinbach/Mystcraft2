package net.mystcraft.fabric.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.mystcraft.minecraft.age.AgeServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Checkpoint ingress before a source chunk can be written/unloaded with its feature stage complete. */
@Mixin(ChunkMap.class)
public abstract class DeferredCrystalChunkSaveMixin {
    @Accessor("level")
    protected abstract ServerLevel mystcraft$level();
    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"))
    private void mystcraft$checkpointCrystals(ChunkAccess chunk, CallbackInfoReturnable<Boolean> callback) {
        ServerLevel level = mystcraft$level();
        if (level instanceof AgeServerLevel age && age.deferredCrystalWrites() != null)
            age.deferredCrystalWrites().flushForSave(level);
    }
}
