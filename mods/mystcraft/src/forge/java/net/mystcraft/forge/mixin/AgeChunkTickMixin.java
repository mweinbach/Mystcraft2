package net.mystcraft.forge.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.mystcraft.minecraft.age.AgeServerLevel;
import net.mystcraft.minecraft.instability.InstabilityRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Profiling and effects follow actual loaded ticking chunks, not force-loaded scan jobs. */
@Mixin(ServerLevel.class)
public abstract class AgeChunkTickMixin {
    @Inject(method = "tickChunk", at = @At("RETURN"))
    private void mystcraft$tickClassicChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level instanceof AgeServerLevel age) age.tickClassicChunk(chunk);
        else if (level.dimension().equals(Level.OVERWORLD)) InstabilityRuntime.profileBaseline(level, chunk);
    }
}
