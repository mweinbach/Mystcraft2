package ports.buildcraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.buildcraft.energy.OilBiomes;

/** Runs the oil biome layers over every biome that the multi-noise source picks, as upstream's layers did. */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class OilBiomeNoiseMixin {
    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"), cancellable = true)
    private void buildcraft$placeOilBiomes(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
        OilBiomes.State state = OilBiomes.current();
        // 1.12's visible biome layer had block resolution, so the noise is read at the quart's first block.
        if (state != null) callback.setReturnValue(state.replace(callback.getReturnValue(), QuartPos.toBlock(x), QuartPos.toBlock(z)));
    }
}
