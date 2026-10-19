package net.mystcraft.forge;

import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mystcraft.minecraft.writing.SymbolTuning;

/** Configure watcher/rank tables before spawn-chunk profiling starts. No client class references. */
@Mod.EventBusSubscriber(modid = "mystcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeSymbolTuning {
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event) { SymbolTuning.initialize(event.getServer()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SymbolTuning.clear(event.getServer()); }
}
