package ports.jurassicraft.compat.jade;

import ports.jurassicraft.compat.jade.client.DinosaurTooltip;
import ports.jurassicraft.compat.jade.client.FeederTooltip;
import ports.jurassicraft.compat.jade.client.IncubatorTooltip;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.feeder.FeederBlock;
import ports.jurassicraft.feeder.FeederBlockEntity;
import ports.jurassicraft.hatching.HatcheryBlock;
import ports.jurassicraft.hatching.HatcheryBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Loaded by Jade only when it is installed; the rest of JurassiCraft does not depend on it. */
@WailaPlugin("jurassicraft")
public final class JurassicJadePlugin implements IWailaPlugin {
    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(ServerProviders.DINOSAUR, DinosaurEntity.class);
        registration.registerBlockDataProvider(ServerProviders.FEEDER, FeederBlockEntity.class);
        registration.registerBlockDataProvider(ServerProviders.INCUBATOR, HatcheryBlockEntity.class);
    }
    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(DinosaurTooltip.INSTANCE, DinosaurEntity.class);
        registration.registerBlockComponent(FeederTooltip.INSTANCE, FeederBlock.class);
        registration.registerBlockComponent(IncubatorTooltip.INSTANCE, HatcheryBlock.class);
    }
}
