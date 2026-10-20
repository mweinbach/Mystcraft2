/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.loader.forge.gamecontent.worldgen.ForgeBiomes;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import ports.extremereactors.forge.client.ForgeClientSetup;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.forge.compat.ForgeComputerCraft;
import ports.extremereactors.forge.compat.ForgeMekanism;
import ports.extremereactors.platform.Platform;

/**
 * The Forge entrypoint.
 */
@Mod(Platform.MOD_ID)
public final class ForgeMod {

    public ForgeMod() {

        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        final ForgePlatform platform = new ForgePlatform(modBus);

        Platform.install(platform);
        ExtremeReactors.initialize();
        ForgeBiomes.initialize(modBus);

        modBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, this::onCommonSetup);
        modBus.addListener(EventPriority.NORMAL, false, InterModProcessEvent.class, this::onInterModProcess);
        modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, event -> {

            if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
                CraftingHelper.register(ForgeConfigCondition.SERIALIZER);
            }
        });

        if (FMLEnvironment.dist.isClient()) {
            ForgeClientSetup.initialize(modBus, platform);
        }
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {

            Content.onCommonInit();
            ExtremeReactors.getInstance().onCommonInit();

            if (Platform.isModLoaded("computercraft")) {
                ForgeComputerCraft.initialize();
            }

            if (Platform.isModLoaded(MekanismGas.MOD_ID)) {
                ForgeMekanism.initialize();
            }
        });
    }

    /**
     * Other Forge mods may also send their Extreme Reactors API calls as inter-mod messages carrying a
     * {@link Runnable}, as they did with the original mod.
     */
    private void onInterModProcess(final InterModProcessEvent event) {
        ExtremeReactors.getInstance().onInterModProcess(method -> event.getIMCStream(method::equals)
                .map(message -> message.messageSupplier().get())
                .filter(Runnable.class::isInstance)
                .map(Runnable.class::cast));
    }
}
