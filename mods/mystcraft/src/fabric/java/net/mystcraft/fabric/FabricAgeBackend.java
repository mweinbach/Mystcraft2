package net.mystcraft.fabric;

import java.util.Map;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.mystcraft.fabric.mixin.MinecraftServerAccessor;
import net.mystcraft.fabric.mixin.ServerLevelAgeAccessor;
import net.mystcraft.fabric.mixin.StructureCheckAccessor;
import net.mystcraft.integration.age.AgeRuntime;

public final class FabricAgeBackend implements AgeRuntime.Backend {
    @Override public LevelStorageAccess storage(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).mystcraft$storage();
    }
    @Override public Executor executor(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).mystcraft$executor();
    }
    @Override public Map<ResourceKey<Level>, ServerLevel> levels(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).mystcraft$levels();
    }
    @Override public void prepare(ServerLevel level, long seed) {
        ((StructureCheckAccessor) ((ServerLevelAgeAccessor) level).mystcraft$structureCheck()).mystcraft$seed(seed);
    }
    @Override public void levelsChanged(MinecraftServer server) {}
    @Override public void loaded(MinecraftServer server, ServerLevel level) {
        ServerWorldEvents.LOAD.invoker().onWorldLoad(server, level);
    }
}
