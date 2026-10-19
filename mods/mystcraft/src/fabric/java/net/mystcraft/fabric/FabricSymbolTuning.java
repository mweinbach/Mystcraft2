package net.mystcraft.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.mystcraft.minecraft.writing.SymbolTuning;

/** Configure watcher/rank tables before initial spawn-chunk profiling. */
public final class FabricSymbolTuning {
    private FabricSymbolTuning() {}
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(SymbolTuning::initialize);
        ServerLifecycleEvents.SERVER_STOPPED.register(SymbolTuning::clear);
    }
}
