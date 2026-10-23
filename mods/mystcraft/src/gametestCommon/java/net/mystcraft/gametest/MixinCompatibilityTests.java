package net.mystcraft.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.mystcraft.integration.MystcraftBootstrap;
import net.mystcraft.integration.age.AgeRuntime;
import net.mystcraft.minecraft.age.AgeServerLevel;
import net.mystcraft.minecraft.age.AgeWeatherContext;
import net.mystcraft.minecraft.exploration.LegacyTreasureHooks;

/**
 * Shared bodies for the mixin compatibility GameTests. Both loaders forward every test with their own
 * {@link AgeRuntime.Backend}, which is how Mystcraft itself adds and removes Age levels.
 */
public final class MixinCompatibilityTests {
    private MixinCompatibilityTests() {}

    /** getAllLevels lists a runtime Age, drops it once unloaded, and still runs another mod's injection. */
    public static void allLevelsTracksAges(GameTestHelper helper, AgeRuntime.Backend backend) {
        MinecraftServer server = helper.getLevel().getServer();
        Iterable<ServerLevel> unchanged = server.getAllLevels();
        require(helper, server.getAllLevels() == unchanged, "getAllLevels must reuse its result while no level is added or removed");
        ServerLevel age = createAge(server, "all_levels", weather("normal"));
        boolean listed;
        try {
            listed = contains(server.getAllLevels(), age);
        } finally {
            unload(server, backend, age);
        }
        require(helper, listed, "a newly created Age must be listed by getAllLevels");
        require(helper, !contains(server.getAllLevels(), age), "an unloaded Age must leave getAllLevels");
        int calls = AllLevelsProbe.calls();
        server.getAllLevels();
        require(helper, AllLevelsProbe.calls() == calls + 1, "a second getAllLevels injection must still run");
        helper.succeed();
    }

    /** The tickChunk weather wraps call through unchanged outside Ages and apply the Age rules inside one. */
    public static void weatherWrapsKeepVanillaOutsideAges(GameTestHelper helper, AgeRuntime.Backend backend) {
        ServerLevel vanilla = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        Biome biome = vanilla.getBiome(pos).value();
        CompoundTag state = weather("snow");
        AgeServerLevel age = createAge(vanilla.getServer(), "weather", state);
        try {
            Method freeze = handler("mystcraft$freeze");
            Method snow = handler("mystcraft$snow");
            Method precipitation = handler("mystcraft$precipitation");
            AtomicInteger originals = new AtomicInteger();
            // True only while no Age climate override is in effect.
            Operation<Boolean> naturalClimate = args -> {
                originals.incrementAndGet();
                return AgeWeatherContext.temperature(0.5F) == 0.5F;
            };
            Operation<Biome.Precipitation> none = args -> { originals.incrementAndGet(); return Biome.Precipitation.NONE; };
            Operation<Biome.Precipitation> rain = args -> { originals.incrementAndGet(); return Biome.Precipitation.RAIN; };

            // Non-age level: exactly the original result, through exactly one original call.
            expect(helper, originals, 1, true, invoke(freeze, vanilla, biome, vanilla, pos, naturalClimate), "vanilla shouldFreeze");
            expect(helper, originals, 1, true, invoke(snow, vanilla, biome, vanilla, pos, naturalClimate), "vanilla shouldSnow");
            expect(helper, originals, 1, Biome.Precipitation.NONE, invoke(precipitation, vanilla, biome, none), "vanilla getPrecipitation (none)");
            expect(helper, originals, 1, Biome.Precipitation.RAIN, invoke(precipitation, vanilla, biome, rain), "vanilla getPrecipitation (rain)");

            // Snow Age: the original runs under the Age climate and precipitation is forced to snow.
            expect(helper, originals, 1, false, invoke(freeze, age, biome, age, pos, naturalClimate), "snow Age shouldFreeze");
            expect(helper, originals, 1, false, invoke(snow, age, biome, age, pos, naturalClimate), "snow Age shouldSnow");
            expect(helper, originals, 1, Biome.Precipitation.SNOW, invoke(precipitation, age, biome, none), "snow Age getPrecipitation");

            // Cloudy Age: no snow at all, and no precipitation regardless of the biome.
            state.putString("MystcraftWeather", "cloudy");
            expect(helper, originals, 0, false, invoke(snow, age, biome, age, pos, naturalClimate), "cloudy Age shouldSnow");
            expect(helper, originals, 1, Biome.Precipitation.NONE, invoke(precipitation, age, biome, rain), "cloudy Age getPrecipitation");

            // Normal Age: natural climate and precipitation.
            state.putString("MystcraftWeather", "normal");
            expect(helper, originals, 1, true, invoke(freeze, age, biome, age, pos, naturalClimate), "normal Age shouldFreeze");
            expect(helper, originals, 1, Biome.Precipitation.RAIN, invoke(precipitation, age, biome, rain), "normal Age getPrecipitation");
        } finally {
            unload(vanilla.getServer(), backend, age);
        }
        helper.succeed();
    }

    /** The legacy treasure chests carry exactly the entries the raw-JSON hook produced. */
    public static void legacyTreasureLootMatchesJsonHook(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Gson gson = Deserializers.createLootTableSerializer().create();
        for (ResourceLocation id : LegacyTreasureHooks.TARGETS) {
            ResourceLocation file = new ResourceLocation(id.getNamespace(), "loot_tables/" + id.getPath() + ".json");
            JsonElement raw;
            try (Reader reader = server.getResourceManager().getResource(file).orElseThrow().openAsReader()) {
                raw = JsonParser.parseReader(reader);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            JsonElement hooked = LegacyTreasureHooks.apply(Map.of(id, raw)).get(id);
            require(helper, !hooked.equals(raw), "the JSON hook must add its empty entry to " + id);
            JsonArray expectedPools = hooked.getAsJsonObject().getAsJsonArray("pools");
            JsonArray actualPools = LootTables.serialize(server.getLootTables().get(id)).getAsJsonObject().getAsJsonArray("pools");
            require(helper, expectedPools.size() == actualPools.size(), id + " pool count changed");
            for (int pool = 0; pool < expectedPools.size(); pool++) {
                JsonArray expected = new JsonArray();
                // Normalize through the loot serializer so defaults and number formats match.
                for (JsonElement entry : expectedPools.get(pool).getAsJsonObject().getAsJsonArray("entries"))
                    expected.add(gson.toJsonTree(gson.fromJson(entry, LootPoolEntryContainer.class)));
                JsonElement actual = actualPools.get(pool).getAsJsonObject().get("entries");
                require(helper, expected.equals(actual), id + " pool " + pool + " entries differ: expected " + expected + ", got " + actual);
            }
        }
        helper.succeed();
    }

    private static CompoundTag weather(String mode) {
        CompoundTag state = new CompoundTag();
        state.putString("MystcraftWeather", mode);
        return state;
    }

    private static AgeServerLevel createAge(MinecraftServer server, String name, CompoundTag state) {
        var registries = server.registryAccess();
        Registry<StructureSet> structures = registries.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        var generator = new FlatLevelSource(structures,
                FlatLevelGeneratorSettings.getDefault(registries.registryOrThrow(Registry.BIOME_REGISTRY), structures));
        Holder<DimensionType> type = registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY)
                .getHolderOrThrow(ResourceKey.create(Registry.DIMENSION_TYPE_REGISTRY, new ResourceLocation(MystcraftBootstrap.MOD_ID, "age")));
        ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(MystcraftBootstrap.MOD_ID, "gametest_" + name));
        return (AgeServerLevel) AgeRuntime.create(server, key, name.hashCode(), generator, type, state, () -> {});
    }

    /** Mirrors AgeRuntime's rollback: remove from the level map, notify the loader, then close. */
    private static void unload(MinecraftServer server, AgeRuntime.Backend backend, ServerLevel level) {
        backend.levels(server).remove(level.dimension(), level);
        backend.levelsChanged(server);
        try {
            level.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static boolean contains(Iterable<ServerLevel> levels, ServerLevel level) {
        for (ServerLevel candidate : levels) if (candidate == level) return true;
        return false;
    }

    /** Mixin renames applied injector handlers (e.g. {@code wrapOperation$...$mystcraft$freeze}); a bare name means no wrap. */
    private static Method handler(String name) {
        for (Method method : ServerLevel.class.getDeclaredMethods()) {
            if (method.getName().endsWith("$" + name)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("No applied @WrapOperation handler " + name + " in ServerLevel");
    }

    private static Object invoke(Method handler, ServerLevel target, Object... args) {
        try {
            return handler.invoke(target, args);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(failure.getCause());
        }
    }

    private static void expect(GameTestHelper helper, AtomicInteger originals, int calls, Object expected, Object actual, String label) {
        int made = originals.getAndSet(0);
        require(helper, Objects.equals(expected, actual), label + ": expected " + expected + ", got " + actual);
        require(helper, made == calls, label + ": expected " + calls + " original call(s), got " + made);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
