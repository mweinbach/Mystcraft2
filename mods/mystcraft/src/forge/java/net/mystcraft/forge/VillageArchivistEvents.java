package net.mystcraft.forge;

import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mystcraft.minecraft.exploration.VillageArchivistPools;

@Mod.EventBusSubscriber(modid = "mystcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageArchivistEvents {
    private VillageArchivistEvents() {}
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event) { VillageArchivistPools.initialize(event.getServer()); }
}
