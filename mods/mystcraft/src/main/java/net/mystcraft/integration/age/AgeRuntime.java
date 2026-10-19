package net.mystcraft.integration.age;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.mystcraft.minecraft.age.AgeLevelData;
import net.mystcraft.minecraft.age.AgeServerLevel;

/** Runtime dimensions use stable saved keys and already synchronized dimension types. */
public final class AgeRuntime {
    public interface Backend {
        LevelStorageAccess storage(MinecraftServer server);
        Executor executor(MinecraftServer server);
        Map<ResourceKey<Level>, ServerLevel> levels(MinecraftServer server);
        void prepare(ServerLevel level, long seed);
        void levelsChanged(MinecraftServer server);
        void loaded(MinecraftServer server, ServerLevel level);
    }

    private static Backend backend;
    private AgeRuntime() {}
    public static void install(Backend implementation) { backend = Objects.requireNonNull(implementation); }

    public static ServerLevel create(MinecraftServer server, ResourceKey<Level> key, long seed,
                                     ChunkGenerator generator, Holder<DimensionType> type) {
        return create(server, key, seed, generator, type, new CompoundTag(), () -> {});
    }

    /** The supplied state belongs to SavedData and must retain its identity until server shutdown. */
    public static ServerLevel create(MinecraftServer server, ResourceKey<Level> key, long seed,
                                     ChunkGenerator generator, Holder<DimensionType> type,
                                     CompoundTag state, Runnable dirty) {
        if (!server.isSameThread()) throw new IllegalStateException("Age creation must run on the server thread");
        if (backend == null) throw new IllegalStateException("Mystcraft age backend is not initialized");
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            if (existing instanceof AgeServerLevel && existing.getSeed() == seed) return existing;
            throw new IllegalStateException("Age key collides with an existing dimension: " + key.location());
        }
        if (type.unwrapKey().isEmpty()) throw new IllegalArgumentException("Age dimension type must be registered");
        AgeLevelData data = new AgeLevelData(server.getWorldData(), state, dirty);
        ServerLevel level = AgeServerLevel.create(server, backend.executor(server), backend.storage(server),
                data, key, new LevelStem(type, generator), seed);
        boolean inserted = false;
        try {
            backend.prepare(level, seed);
            level.getWorldBorder().applySettings(data.getWorldBorder());
            data.trackBorder(level.getWorldBorder());
            backend.levels(server).put(key, level);
            inserted = true;
            backend.levelsChanged(server);
            backend.loaded(server, level);
            dirty.run();
            return level;
        } catch (RuntimeException | Error failure) {
            if (inserted) {
                backend.levels(server).remove(key, level);
                backend.levelsChanged(server);
            }
            try { level.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }
}
