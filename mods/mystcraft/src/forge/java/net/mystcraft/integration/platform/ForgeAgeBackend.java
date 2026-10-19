package net.mystcraft.integration.platform;

import java.util.Map;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.mystcraft.integration.age.AgeRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Forge exposes the level map; mapped reflection accesses the two vanilla constructor services. */
public final class ForgeAgeBackend implements AgeRuntime.Backend {
    @Override public LevelStorageAccess storage(MinecraftServer server) {
        return ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, server, "f_129744_");
    }
    @Override public Executor executor(MinecraftServer server) {
        return ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, server, "f_129738_");
    }
    @Override public Map<ResourceKey<Level>, ServerLevel> levels(MinecraftServer server) {
        return server.forgeGetWorldMap();
    }
    @Override public void prepare(ServerLevel level, long seed) {
        StructureCheck check = ObfuscationReflectionHelper.getPrivateValue(ServerLevel.class, level, "f_196556_");
        ObfuscationReflectionHelper.setPrivateValue(StructureCheck.class, check, seed, "f_197245_");
    }
    @Override public void levelsChanged(MinecraftServer server) { server.markWorldsDirty(); }
    @Override public void loaded(MinecraftServer server, ServerLevel level) {
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(level));
    }
}
