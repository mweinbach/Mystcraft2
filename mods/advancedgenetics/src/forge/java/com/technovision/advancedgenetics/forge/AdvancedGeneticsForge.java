package com.technovision.advancedgenetics.forge;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.forge.client.AdvancedGeneticsForgeClient;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AdvancedGenetics.MOD_ID)
public class AdvancedGeneticsForge {

    public AdvancedGeneticsForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Platform.install(new ForgePlatform(modBus));
        ForgeConfig.register(modBus);
        AdvancedGenetics.init();
        ForgeNetwork.register();
        ForgeCapabilityEvents.register(modBus);
        ForgeGeneticsEvents.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AdvancedGeneticsForgeClient.init(modBus));
    }
}
