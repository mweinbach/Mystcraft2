package net.mystcraft.integration;

import net.mystcraft.integration.network.ForgeLinkingTransport;
import net.mystcraft.integration.platform.ForgeRegistryBridge;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.integration.platform.ForgeGameplayPlatform;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge entrypoint. Gameplay and registry definitions are shared with Fabric. */
@Mod(Mystcraft.MOD_ID)
public final class Mystcraft {
    public static final String MOD_ID = MystcraftBootstrap.MOD_ID;
    public Mystcraft() {
        GameplayPlatform.install(new ForgeGameplayPlatform());
        net.mystcraft.integration.age.AgeRuntime.install(new net.mystcraft.integration.platform.ForgeAgeBackend());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartingEvent event) -> net.mystcraft.minecraft.age.AgeManager.restore(event.getServer()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) -> net.mystcraft.minecraft.age.AgeManager.clear(event.getServer()));
        ForgeRegistryBridge bridge = new ForgeRegistryBridge();
        net.mystcraft.minecraft.writing.DynamicSymbolPages.setFluidName(fluid -> fluid.getFluidType().getDescription());
        net.mystcraft.minecraft.writing.DynamicSymbolPages.setFluidEligibility(fluid -> !fluid.getFluidType().isLighterThanAir() && fluid.getFluidType().getDensity() >= 0);
        MystcraftBootstrap.initialize(bridge);
        net.mystcraft.forge.fluid.ForgeInkAutomation.initialize();
        bridge.attach(FMLJavaModLoadingContext.get().getModEventBus());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) ->
                net.mystcraft.minecraft.command.MystcraftCommands.register(event.getDispatcher()));
        ForgeLinkingTransport.register();
        net.mystcraft.integration.network.ForgeAgeVisualTransport.initialize();
        net.mystcraft.integration.network.ForgeMenuActionTransport.initialize();
        net.mystcraft.integration.exploration.ForgeExplorationHooks.initialize(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
