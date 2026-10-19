package net.mystcraft.minecraft.age;

import com.mojang.serialization.Dynamic;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerCallback;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;

/** Independent age state backed directly by the owning age's SavedData compound. */
public final class AgeLevelData extends DerivedLevelData {
    private final CompoundTag state;
    private final Runnable dirty;
    private final GameRules rules;
    private final TimerQueue<MinecraftServer> scheduledEvents;

    public AgeLevelData(WorldData worldData, CompoundTag state, Runnable dirty) {
        super(worldData, worldData.overworldData());
        this.state = state;
        this.dirty = dirty;
        if (!state.contains("SpawnY")) state.putInt("SpawnY", 80);
        if (!state.contains("GameType")) state.putInt("GameType", worldData.overworldData().getGameType().getId());
        if (!state.contains("GameRules")) state.put("GameRules", worldData.overworldData().getGameRules().createTag());
        rules = new GameRules(new Dynamic<>(NbtOps.INSTANCE, state.getCompound("GameRules")));
        scheduledEvents = new PersistentTimers(state, dirty);
    }

    CompoundTag state() { return state; }
    boolean classicRuntimeEnabled() { return state.getInt("ClassicRuntimeVersion") == 1; }
    void markDirty() { dirty.run(); }
    public String weatherMode() { return state.getString("MystcraftWeather"); }
    public boolean weatherActive() { return !AgeWeatherRules.toggleable(weatherMode()) || AgeWeatherRules.active(state); }
    public boolean pvpEnabled() { return !state.contains("MystcraftPvp") || state.getBoolean("MystcraftPvp"); }

    private void integer(String key, int value) { state.putInt(key, value); dirty.run(); }
    private void bool(String key, boolean value) { state.putBoolean(key, value); dirty.run(); }
    @Override public int getXSpawn() { return state.getInt("SpawnX"); }
    @Override public int getYSpawn() { return state.getInt("SpawnY"); }
    @Override public int getZSpawn() { return state.getInt("SpawnZ"); }
    @Override public float getSpawnAngle() { return state.getFloat("SpawnAngle"); }
    @Override public void setXSpawn(int value) { integer("SpawnX", value); }
    @Override public void setYSpawn(int value) { integer("SpawnY", value); }
    @Override public void setZSpawn(int value) { integer("SpawnZ", value); }
    @Override public void setSpawnAngle(float value) { state.putFloat("SpawnAngle", value); dirty.run(); }
    @Override public void setSpawn(BlockPos pos, float angle) {
        setXSpawn(pos.getX()); setYSpawn(pos.getY()); setZSpawn(pos.getZ()); setSpawnAngle(angle);
    }
    @Override public long getGameTime() { return state.contains("GameTime") ? state.getLong("GameTime") : state.getLong("Time"); }
    @Override public long getDayTime() { return state.getLong("DayTime"); }
    @Override public void setGameTime(long value) {
        state.putLong("GameTime", value);
        state.put("GameRules", rules.createTag());
        dirty.run();
    }
    @Override public void setDayTime(long value) { state.putLong("DayTime", value); dirty.run(); }
    @Override public int getClearWeatherTime() { return state.getInt("ClearWeatherTime"); }
    @Override public void setClearWeatherTime(int value) { integer("ClearWeatherTime", value); }
    @Override public boolean isThundering() { return AgeWeatherRules.toggleable(weatherMode()) ? AgeWeatherRules.thundering(state) : fixedWeather() ? false : state.getBoolean("Thundering"); }
    @Override public void setThundering(boolean value) {
        if (AgeWeatherRules.toggleable(weatherMode())) {
            if (weatherMode().equals("storm")) { if (value) AgeWeatherRules.enable(state); else AgeWeatherRules.disable(state); dirty.run(); }
            return;
        }
        bool("Thundering", !fixedWeather() && value);
    }
    @Override public int getThunderTime() { return state.getInt("ThunderTime"); }
    @Override public void setThunderTime(int value) { integer("ThunderTime", value); }
    @Override public boolean isRaining() {
        if (AgeWeatherRules.toggleable(weatherMode())) return AgeWeatherRules.raining(state);
        String mode = state.getString("MystcraftWeather");
        return mode.equals("always") || !mode.equals("off") && state.getBoolean("Raining");
    }
    @Override public void setRaining(boolean value) {
        if (AgeWeatherRules.toggleable(weatherMode())) {
            if (value) AgeWeatherRules.enable(state); else AgeWeatherRules.disable(state);
            dirty.run(); return;
        }
        String mode = state.getString("MystcraftWeather");
        bool("Raining", mode.equals("always") || !mode.equals("off") && value);
    }
    @Override public int getRainTime() { return state.getInt("RainTime"); }
    @Override public void setRainTime(int value) { integer("RainTime", value); }
    @Override public GameType getGameType() { return GameType.byId(state.getInt("GameType")); }
    @Override public void setGameType(GameType value) { integer("GameType", value.getId()); }
    @Override public boolean isInitialized() { return state.getBoolean("Initialized"); }
    @Override public void setInitialized(boolean value) { bool("Initialized", value); }
    @Override public GameRules getGameRules() { return rules; }
    @Override public WorldBorder.Settings getWorldBorder() {
        return WorldBorder.Settings.read(new Dynamic<>(NbtOps.INSTANCE, state), WorldBorder.DEFAULT_SETTINGS);
    }
    @Override public void setWorldBorder(WorldBorder.Settings settings) { settings.write(state); dirty.run(); }
    /** Persist border commands immediately; vanilla only snapshots the overworld border on save. */
    public void trackBorder(WorldBorder border) {
        border.addListener(new BorderChangeListener() {
            private void changed(WorldBorder value) { setWorldBorder(value.createSettings()); }
            @Override public void onBorderSizeSet(WorldBorder value, double size) { changed(value); }
            @Override public void onBorderSizeLerping(WorldBorder value, double from, double to, long time) { changed(value); }
            @Override public void onBorderCenterSet(WorldBorder value, double x, double z) { changed(value); }
            @Override public void onBorderSetWarningTime(WorldBorder value, int time) { changed(value); }
            @Override public void onBorderSetWarningBlocks(WorldBorder value, int blocks) { changed(value); }
            @Override public void onBorderSetDamagePerBlock(WorldBorder value, double damage) { changed(value); }
            @Override public void onBorderSetDamageSafeZOne(WorldBorder value, double distance) { changed(value); }
        });
    }
    @Override public TimerQueue<MinecraftServer> getScheduledEvents() { return scheduledEvents; }
    @Override public int getWanderingTraderSpawnDelay() { return state.getInt("TraderDelay"); }
    @Override public void setWanderingTraderSpawnDelay(int value) { integer("TraderDelay", value); }
    @Override public int getWanderingTraderSpawnChance() { return state.getInt("TraderChance"); }
    @Override public void setWanderingTraderSpawnChance(int value) { integer("TraderChance", value); }
    @Override public UUID getWanderingTraderId() { return state.hasUUID("TraderId") ? state.getUUID("TraderId") : null; }
    @Override public void setWanderingTraderId(UUID value) {
        if (value == null) state.remove("TraderId"); else state.putUUID("TraderId", value);
        dirty.run();
    }

    private boolean fixedWeather() {
        String mode = state.getString("MystcraftWeather");
        return mode.equals("off") || mode.equals("always");
    }

    /** Each classic weather controller affects only this Age and retains its own persisted timers. */
    public void enforceWeather(MinecraftServer server) {
        // The classic controller owns its timers. Vanilla still interpolates and synchronizes
        // rain/thunder strengths, but must not decrement or toggle the same counters a second time.
        if (rules.getBoolean(GameRules.RULE_WEATHER_CYCLE)) rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        if (AgeWeatherRules.toggleable(weatherMode()) || !fixedWeather()) return;
        boolean raining = state.getString("MystcraftWeather").equals("always");
        if (state.getBoolean("Raining") != raining) setRaining(raining);
        if (state.getBoolean("Thundering")) setThundering(false);
        int clear = raining ? 0 : 12000;
        if (getClearWeatherTime() != clear) setClearWeatherTime(clear);
        if (getRainTime() != 12000) setRainTime(12000);
        if (getThunderTime() != 12000) setThunderTime(12000);
    }

    /** This immutable frozen plan is read once by the owning ServerLevel. Old tags retain vanilla sky. */
    public net.mystcraft.minecraft.age.visual.AgeCelestialSettings celestials() {
        if (!state.contains(net.mystcraft.minecraft.age.visual.AgeVisualSettings.STATE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND))
            return net.mystcraft.minecraft.age.visual.AgeCelestialSettings.LEGACY;
        return net.mystcraft.minecraft.age.visual.AgeVisualSettings.fromTag(state.getCompound(
                net.mystcraft.minecraft.age.visual.AgeVisualSettings.STATE_KEY)).celestials();
    }

    /** Called once per real level tick, not during construction or client rendering. */
    public void advanceWeather(long ageSeed) {
        if (fixedWeather()) return;
        if (AgeWeatherRules.toggleable(weatherMode())) AgeWeatherRules.tick(state);
        else AgeWeatherCycle.tick(state, ageSeed);
        dirty.run();
    }

    private static final class PersistentTimers extends TimerQueue<MinecraftServer> {
        private CompoundTag state;
        private Runnable dirty;
        PersistentTimers(CompoundTag state, Runnable dirty) {
            super(TimerCallbacks.SERVER_CALLBACKS, state.getList("ScheduledEvents", 10).stream()
                    .map(tag -> new Dynamic<>(NbtOps.INSTANCE, tag)));
            this.state = state;
            this.dirty = dirty;
        }
        private void changed() {
            if (state != null) { state.put("ScheduledEvents", store()); dirty.run(); }
        }
        @Override public void schedule(String name, long time, TimerCallback<MinecraftServer> callback) {
            super.schedule(name, time, callback); changed();
        }
        @Override public int remove(String name) { int removed = super.remove(name); if (removed != 0) changed(); return removed; }
        @Override public void tick(MinecraftServer server, long time) {
            boolean nonempty = !getEventsIds().isEmpty();
            super.tick(server, time);
            if (nonempty) changed();
        }
    }
}
