package ports.jurassicraft.forge.mixin;

import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.jurassicraft.structures.ParkEdges;

/**
 * Vanilla post-processing runs once, just before a chunk starts ticking, which is before a player
 * can edit it. Park pieces replay their neighbour cascade at that moment if the rest of the
 * footprint is already loaded.
 */
@Mixin(LevelChunk.class)
public class ParkEdgePostProcessMixin {
    @Inject(method = "postProcessGeneration", at = @At("RETURN"))
    private void jurassicraft$settleEdges(CallbackInfo ci) {
        ParkEdges.settleLoaded((LevelChunk) (Object) this);
    }
}
