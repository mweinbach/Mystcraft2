package net.mystcraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.mystcraft.integration.MystcraftBootstrap;
import net.mystcraft.integration.platform.GameplayPlatform;

public final class FabricMystcraft implements ModInitializer {
    @Override public void onInitialize() {
        GameplayPlatform.install(new FabricGameplayBackend());
        net.mystcraft.minecraft.writing.DynamicSymbolPages.setFluidName(fluid -> net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid)));
        net.mystcraft.minecraft.writing.DynamicSymbolPages.setFluidEligibility(fluid -> !net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.isLighterThanAir(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid)));
        net.mystcraft.integration.age.AgeRuntime.install(new FabricAgeBackend());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(net.mystcraft.minecraft.age.AgeManager::restore);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(net.mystcraft.minecraft.age.AgeManager::clear);
        MystcraftBootstrap.initialize(new FabricRegistryBridge());
        net.mystcraft.fabric.exploration.FabricLecternInventory.initialize();
        FabricSymbolTuning.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(net.mystcraft.minecraft.exploration.VillageArchivistPools::initialize);
        net.mystcraft.fabric.fluid.FabricInkAutomation.initialize();
        FabricLinkingTransport.initialize();
        FabricLinkingRulesEvents.initialize();
        FabricAgeVisualTransport.initialize();
        FabricMenuActionTransport.initialize();
        FabricExplorationHooks.initialize();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, access, selection) ->
                net.mystcraft.minecraft.command.MystcraftCommands.register(dispatcher));
    }
}
