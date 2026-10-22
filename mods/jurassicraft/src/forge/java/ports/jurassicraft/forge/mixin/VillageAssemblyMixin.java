package ports.jurassicraft.forge.mixin;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.jurassicraft.village.VillageLimits;

/** Counts a village building only after jigsaw assembly accepts the piece. */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class VillageAssemblyMixin {
    @Shadow @Final private RandomSource random;
    @Shadow @Final private List<PoolElementStructurePiece> pieces;

    @Inject(method = "tryPlacingChildren", at = @At("HEAD"))
    private void jurassicraft$beginVillage(CallbackInfo ci) {
        VillageLimits.begin(this.random);
    }

    @Inject(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void jurassicraft$recordVillagePiece(CallbackInfo ci) {
        VillageLimits.record(this.pieces.get(this.pieces.size() - 1).getElement());
    }

    @Inject(method = "tryPlacingChildren", at = @At("RETURN"))
    private void jurassicraft$endVillage(CallbackInfo ci) {
        VillageLimits.end();
    }
}
