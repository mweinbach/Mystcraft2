package ports.buildcraft.mixin;

import java.util.Collection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ports.buildcraft.energy.OilBiomes;

/**
 * Upstream's oil biomes extend the desert and the ocean, so they get those biomes' surfaces: a surface rule's
 * biome condition naming the parent also passes the oil biome. The listed biomes, which the codec writes, stay as given.
 * Only the argument of the constructor's {@code Set.copyOf} changes, so other mods' injectors there still run; a plain
 * {@code @ModifyArg} needs no MixinExtras, which Forge 1.19.2 does not ship.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$BiomeConditionSource")
public abstract class OilBiomeSurfaceRuleMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Set;copyOf(Ljava/util/Collection;)Ljava/util/Set;"))
    private Collection<ResourceKey<Biome>> buildcraft$matchOilBiomes(Collection<ResourceKey<Biome>> biomes) {
        return OilBiomes.withOilBiomeKeys(biomes);
    }
}
