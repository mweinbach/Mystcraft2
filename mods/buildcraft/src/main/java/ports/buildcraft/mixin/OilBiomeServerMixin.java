package ports.buildcraft.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.buildcraft.energy.OilBiomes;

/**
 * Upstream {@code BiomeInitializer} got the world seed with the overworld's biome provider. Install the oil
 * biome layers for the overworld's biome source when the server is created, before any level reads its
 * possible biomes; levels take their generators from these same level stems.
 */
@Mixin(MinecraftServer.class)
public abstract class OilBiomeServerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void buildcraft$installOilBiomes(CallbackInfo callback) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        WorldGenSettings settings = server.getWorldData().worldGenSettings();
        LevelStem overworld = settings.dimensions().get(LevelStem.OVERWORLD);
        OilBiomes.install(server.registryAccess(), settings.seed(), overworld == null ? null : overworld.generator().getBiomeSource());
    }
}
