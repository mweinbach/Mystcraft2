package net.mystcraft.minecraft.age;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.mystcraft.minecraft.age.visual.AgeCelestialSettings;
import net.mystcraft.minecraft.age.visual.CelestialMath;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;

/** Vanilla calls getSeed while constructing chunk storage, before subclass fields initialize. */
public final class AgeServerLevel extends ServerLevel {
    private static final ThreadLocal<Long> CONSTRUCTION_SEED = new ThreadLocal<>();
    private static final ChunkProgressListener PROGRESS = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos pos) {}
        @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) {}
        @Override public void start() {}
        @Override public void stop() {}
    };
    private final long ageSeed;
    private boolean seedInitialized;
    private AgeLevelData ageData;
    private AgeCelestialSettings celestials;
    private boolean sleepAdvanceRequested;
    private net.mystcraft.minecraft.age.generation.DeferredCrystalWrites deferredCrystals;
    public net.mystcraft.minecraft.age.generation.DeferredCrystalWrites deferredCrystalWrites() { return deferredCrystals; }

    private AgeServerLevel(MinecraftServer server, Executor executor, LevelStorageAccess storage,
                           AgeLevelData data, ResourceKey<Level> key, LevelStem stem, long seed) {
        super(server, executor, storage, data, key, stem, PROGRESS, false,
                BiomeManager.obfuscateSeed(seed), List.of(), true);
        ageSeed = seed;
        seedInitialized = true;
        ageData = data;
        celestials = data.celestials();
        data.enforceWeather(server);
        deferredCrystals = new net.mystcraft.minecraft.age.generation.DeferredCrystalWrites(this);
    }

    public static AgeServerLevel create(MinecraftServer server, Executor executor, LevelStorageAccess storage,
                                        AgeLevelData data, ResourceKey<Level> key, LevelStem stem, long seed) {
        Long previous = CONSTRUCTION_SEED.get();
        CONSTRUCTION_SEED.set(seed);
        try { return new AgeServerLevel(server, executor, storage, data, key, stem, seed); }
        finally {
            if (previous == null) CONSTRUCTION_SEED.remove();
            else CONSTRUCTION_SEED.set(previous);
        }
    }

    @Override public long getSeed() {
        if (seedInitialized) return ageSeed;
        Long constructing = CONSTRUCTION_SEED.get();
        if (constructing == null) throw new IllegalStateException("Age seed requested outside construction scope");
        return constructing;
    }

    /** Ages do not have the Overworld's permanently loaded vanilla spawn region. */
    @Override public void setDefaultSpawnPos(net.minecraft.core.BlockPos position, float angle) {
        // Calling super installs START radius 11 (529 full chunks) for every new Age.
        // Spawn data is independent and persistent; only readers in this dimension need its packet.
        var old = new ChunkPos(getSharedSpawnPos());
        ((net.minecraft.world.level.storage.WritableLevelData) getLevelData()).setSpawn(position, angle);
        getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.START, old, 11,
                net.minecraft.util.Unit.INSTANCE);
        getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.START, new ChunkPos(position), 11,
                net.minecraft.util.Unit.INSTANCE);
        var packet = new net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket(position, angle);
        for (var player : players()) player.connection.send(packet);
    }

    public boolean isPvpEnabled() { return ageData == null || ageData.pvpEnabled(); }

    public String weatherMode() { return ageData == null ? "normal" : ageData.weatherMode(); }
    public boolean weatherActive() { return ageData == null || ageData.weatherActive(); }

    @Override public boolean isRainingAt(net.minecraft.core.BlockPos pos) {
        if (!AgeWeatherRules.toggleable(weatherMode())) return super.isRainingAt(pos);
        if (!isRaining() || !canSeeSky(pos) || getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) return false;
        var biome = getBiome(pos).value();
        return AgeWeatherRules.precipitation(weatherMode(), biome.getPrecipitation(), weatherActive()) == net.minecraft.world.level.biome.Biome.Precipitation.RAIN
                && AgeWeatherContext.with(this, () -> biome.warmEnoughToRain(pos));
    }

    /** Called only for chunks vanilla actually ticks; it never loads a neighboring chunk. */
    public void tickClassicChunk(net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (ageData != null && ageData.classicRuntimeEnabled()) net.mystcraft.minecraft.instability.InstabilityRuntime.tickChunk(
                this, chunk, ageData.state(), ageData::markDirty);
    }

    /** Level's sun angle and server skylight calculations call this virtual time-of-day hook. */
    @Override public float getTimeOfDay(float partialTick) {
        if (celestials == null || celestials.legacyVanilla()) return super.getTimeOfDay(partialTick);
        return CelestialMath.daylightAngle(celestials, getDayTime(), partialTick);
    }

    @Override public void setDayTime(long value) {
        if (sleepAdvanceRequested && celestials != null && !celestials.legacyVanilla()) {
            long current = getDayTime();
            // Only replace vanilla's actual sleep skip. Tick increments and explicit time
            // commands keep their requested value, including the independent Age clock.
            if (AgeSleepSchedule.isVanillaSleepAdvance(current, value)) {
                sleepAdvanceRequested = false;
                value = sleepTarget(celestials, current);
            }
        }
        super.setDayTime(value);
    }

    public static long sleepTarget(AgeCelestialSettings settings, long current) {
        return AgeSleepSchedule.target(settings, current);
    }

    private boolean enoughDeepSleepers() {
        int active = 0, sleeping = 0;
        for (var player : players()) if (!player.isSpectator()) {
            active++;
            if (player.isSleepingLongEnough()) sleeping++;
        }
        int percentage = getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int required = Math.max(1, (int) Math.ceil(active * (double) percentage / 100));
        return active > 0 && sleeping >= required;
    }

    @Override public void tick(BooleanSupplier haveTime) {
        ageData.enforceWeather(getServer());
        ageData.advanceWeather(ageSeed);
        sleepAdvanceRequested = celestials != null && !celestials.legacyVanilla() && enoughDeepSleepers();
        try { super.tick(haveTime); }
        finally { sleepAdvanceRequested = false; }
        deferredCrystals.tick(this);
    }
    @Override public void save(net.minecraft.util.ProgressListener progress, boolean flush, boolean skipSave) {
        super.save(progress, flush, skipSave);
        if (!skipSave && deferredCrystals != null) deferredCrystals.flushForSave(this);
    }
}
