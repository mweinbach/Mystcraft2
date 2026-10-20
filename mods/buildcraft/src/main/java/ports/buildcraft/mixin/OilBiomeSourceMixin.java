package ports.buildcraft.mixin;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.buildcraft.energy.OilBiomes;

/**
 * Lists the oil biomes among a multi-noise source's possible biomes. Chunk decoration only runs the features
 * of possible biomes, and {@code /locate biome} and Fabric's biome selectors only search them.
 */
@Mixin(BiomeSource.class)
public abstract class OilBiomeSourceMixin {
    @Unique @Nullable private OilBiomes.PossibleBiomes buildcraft$possibleBiomes;

    @Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
    private void buildcraft$addOilBiomes(CallbackInfoReturnable<Set<Holder<Biome>>> callback) {
        OilBiomes.State state = OilBiomes.current();
        if (state == null || !((Object) this instanceof MultiNoiseBiomeSource)) return;
        OilBiomes.PossibleBiomes cached = buildcraft$possibleBiomes;
        if (cached == null || cached.state() != state) {
            cached = new OilBiomes.PossibleBiomes(state, state.withOilBiomes(callback.getReturnValue()));
            buildcraft$possibleBiomes = cached;
        }
        callback.setReturnValue(cached.biomes());
    }
}
