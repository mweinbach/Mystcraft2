package ports.buildcraft.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.buildcraft.energy.OilBiomes;

/**
 * Upstream {@code BiomeInitializer} got the world seed with each biome provider. Install the oil biome layers
 * when the server is created, before any level reads the overworld's possible biomes.
 */
@Mixin(MinecraftServer.class)
public abstract class OilBiomeServerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void buildcraft$installOilBiomes(CallbackInfo callback) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        OilBiomes.install(server.registryAccess(), server.getWorldData().worldGenSettings().seed());
    }
}
