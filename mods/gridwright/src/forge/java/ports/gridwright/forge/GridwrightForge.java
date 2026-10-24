package ports.gridwright.forge;

import ports.gridwright.Gridwright;
import ports.gridwright.forge.client.GridwrightForgeClient;
import ports.gridwright.platform.Platform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Gridwright.MOD_ID)
public class GridwrightForge {

    public GridwrightForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Platform.install(new ForgePlatform(modBus));
        Gridwright.init();
        ForgeCapabilityEvents.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GridwrightForgeClient.init(modBus));
    }
}
