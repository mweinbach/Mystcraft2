package net.mystcraft.minecraft.age;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.mystcraft.minecraft.fissure.StarFissureGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.mystcraft.integration.age.AgeRuntime;
import net.mystcraft.minecraft.linking.LinkDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-authoritative descriptive-book allocation and restart-safe Age lifecycle. */
public final class AgeManager {
    private static final Logger LOG = LoggerFactory.getLogger("mystcraft-ages");
    private AgeManager() {}
    public static AgeSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AgeSavedData::load, AgeSavedData::new, AgeSavedData.FILE);
    }
    public static void restore(MinecraftServer server) {
        net.mystcraft.minecraft.linking.LinkingRulesConfig.get(server);
        for (AgeSavedData.Entry age : data(server).entries()) load(server, age);
        LOG.info("Restored {} persistent Mystcraft Ages", data(server).entries().size());
    }
    public static void clear(MinecraftServer server) {
        net.mystcraft.minecraft.instability.InstabilityRuntime.clear(server);
        net.mystcraft.minecraft.linking.LinkingRulesConfig.clear(server);
    }

    public static ServerLevel ensureLoaded(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel existing = server.getLevel(key);
        if (existing != null) return existing;
        for (AgeSavedData.Entry age : data(server).entries())
            if (age.dimensionId().equals(key.location())) return load(server, age);
        return null;
    }
    /** Invoked only after a book's server-owned activation session has been validated. */
    public static boolean prepare(ServerPlayer player, ItemStack book) { return prepare((Entity) player, book); }

    /** Nonplayer portal users share canonical allocation and validation, without inventing a player inventory. */
    public static boolean prepare(Entity entity, ItemStack book) {
        if (!AgeBookData.isDescriptive(book)) return true;
        if (!(entity.level instanceof ServerLevel origin) || !entity.isAlive()) return false;
        MinecraftServer server = origin.getServer();
        AgeSavedData saved = data(server);
        AgeSavedData.Entry age;
        if (AgeBookData.isUnallocated(book)) {
            long seed;
            try {
                var requested = AgeSeed.requested(book.hasTag() ? book.getTag() : new net.minecraft.nbt.CompoundTag());
                seed = requested.isPresent() ? requested.getAsLong() : origin.random.nextLong();
            } catch (IllegalArgumentException invalid) {
                feedback(entity, Component.translatable("message.mystcraft.invalid_age", "Seed must be a signed decimal long"), false);
                return false;
            }
            AgeAllocation.Prepared prepared;
            try {
                prepared = AgeAllocation.compile(server, AgeBookData.pages(book), seed);
                if (!prepared.compilation().successful()) {
                    feedback(entity, Component.translatable("message.mystcraft.unsupported_symbols",
                            String.join(", ", prepared.compilation().diagnostics())), false);
                    return false;
                }
            } catch (RuntimeException failure) {
                feedback(entity, Component.translatable("message.mystcraft.invalid_age", failure.getMessage()), false);
                return false;
            }
            age = saved.allocatePrepared(seed, book.getHoverName().getString(), AgeBookData.pages(book), prepared);
            if (book.hasTag() && book.getTag().contains("Authors", net.minecraft.nbt.Tag.TAG_LIST))
                age.state().put("Authors", book.getTag().getList("Authors", net.minecraft.nbt.Tag.TAG_STRING).copy());
            AgeBookData.assign(book, age.id(), age.number(), age.seed());
            // Identity must reach disk before a dimension can write its first chunk.
            server.overworld().getDataStorage().save();
        } else {
            age = AgeBookData.ageId(book).map(saved::get).orElse(null);
            if (age == null) {
                feedback(entity, Component.translatable("message.mystcraft.missing_age"), true);
                return false;
            }
        }
        try {
            ServerLevel target = load(server, age);
            if (!age.state().getBoolean("ArrivalReady")) prepareArrival(target, age, saved);
            if (compiledPlan(age).starFissure()) StarFissureGenerator.ensure(target, age, saved);
            new LinkDestination(age.dimensionId(), age.state().getDouble("ArrivalX"),
                    age.state().getDouble("ArrivalY"), age.state().getDouble("ArrivalZ"), 0, 0).write(book);
            net.mystcraft.minecraft.linking.LinkingRules.stampBook(server, target.dimension(), book);
            if (entity instanceof ServerPlayer player) player.getInventory().setChanged();
            return true;
        } catch (RuntimeException failure) {
            LOG.error("Could not open Age {} (identity retained for retry)", age.dimensionId(), failure);
            feedback(entity, Component.translatable("message.mystcraft.invalid_age", failure.getMessage()), false);
            return false;
        }
    }
    private static void feedback(Entity entity, Component message, boolean actionBar) {
        if (entity instanceof ServerPlayer player) player.displayClientMessage(message, actionBar);
        else LOG.warn("Age activation for entity {}: {}", entity.getUUID(), message.getString());
    }
    private static ServerLevel load(MinecraftServer server, AgeSavedData.Entry age) {
        ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, age.dimensionId());
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            if (!(existing instanceof AgeServerLevel) || existing.getSeed() != age.seed())
                throw new IllegalStateException("Age key collides with another world: " + age.dimensionId());
            return existing;
        }
        AgeSymbolCompiler.Plan plan = compiledPlan(age);
        if (!plan.unsupported().isEmpty()) throw new IllegalStateException("Unimplemented saved Age symbols: " + plan.unsupported());
        if (!age.state().contains("GameTime")) {
            age.state().putLong("GameTime", 0); age.state().putLong("DayTime", 6000);
        }
        age.state().putString("MystcraftWeather", plan.weather());
        visualSettings(age);
        ServerLevel level = AgeRuntime.create(server, key, age.seed(), generator(server, plan, age),
                server.registryAccess().registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY)
                        .getHolderOrThrow(ResourceKey.create(Registry.DIMENSION_TYPE_REGISTRY,
                                new net.minecraft.resources.ResourceLocation("mystcraft", "age"))), age.state(), data(server)::setDirty);
        data(server).setDirty();
        LOG.info("Loaded Age {} seed {} using {} terrain", key.location(), age.seed(), plan.terrain());
        return level;
    }
    private static AgeSymbolCompiler.Plan compiledPlan(AgeSavedData.Entry age) {
        var state = age.state();
        if (!state.contains(net.mystcraft.minecraft.age.generation.FrozenAgePlan.KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            state.put(net.mystcraft.minecraft.age.generation.FrozenAgePlan.KEY,
                    net.mystcraft.minecraft.age.generation.FrozenAgePlan.write(AgeSymbolCompiler.parse(age.pages())));
        }
        return net.mystcraft.minecraft.age.generation.FrozenAgePlan.read(state.getCompound(net.mystcraft.minecraft.age.generation.FrozenAgePlan.KEY));
    }
    public static net.mystcraft.minecraft.age.visual.AgeVisualSettings visualSettings(AgeSavedData.Entry age) {
        String key = net.mystcraft.minecraft.age.visual.AgeVisualSettings.STATE_KEY;
        if (!age.state().contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var parsed = net.mystcraft.minecraft.age.visual.AgeVisualCompiler.parse(age.pages(), age.seed()).settings();
            // Missing visual data identifies a pre-celestial saved Age. Retain its previous
            // vanilla sky rather than interpreting old pages as newly randomized celestial bodies.
            var legacy = new net.mystcraft.minecraft.age.visual.AgeVisualSettings(
                    parsed.lighting(), parsed.sky(), parsed.fog(), parsed.water());
            age.state().put(key, legacy.toTag());
        }
        return net.mystcraft.minecraft.age.visual.AgeVisualSettings.fromTag(age.state().getCompound(key));
    }
    public static net.mystcraft.minecraft.age.visual.AgeVisualSettings visualSettings(ServerLevel level) {
        for (AgeSavedData.Entry age : data(level.getServer()).entries()) {
            if (age.dimensionId().equals(level.dimension().location())) {
                boolean existing = age.state().contains(net.mystcraft.minecraft.age.visual.AgeVisualSettings.STATE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND);
                var settings = visualSettings(age);
                if (!existing) data(level.getServer()).setDirty();
                return settings;
            }
        }
        return net.mystcraft.minecraft.age.visual.AgeVisualSettings.DEFAULT;
    }
    private static ChunkGenerator generator(MinecraftServer server, AgeSymbolCompiler.Plan plan, AgeSavedData.Entry age) {
        var state = age.state();
        if (state.contains("GeneratorVersion") && !state.contains("GeneratorVersion", net.minecraft.nbt.Tag.TAG_INT))
            throw new IllegalStateException("Malformed saved Age generator version");
        int version = state.getInt("GeneratorVersion");
        if (version == 0) {
            // Migration never upgrades an existing unversioned Age: unexplored chunks retain
            // the exact previous vanilla generator, including its old flat-world coordinates.
            state.putInt("GeneratorVersion", 0); state.putString("GeneratorBackend", "vanilla_v0");
            return legacyGenerator(server, plan);
        }
        if (state.contains(net.mystcraft.minecraft.age.generation.ClassicGenerationPlan.KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var generation = net.mystcraft.minecraft.age.generation.ClassicGenerationPlan.fromTag(
                    state.getCompound(net.mystcraft.minecraft.age.generation.ClassicGenerationPlan.KEY));
            if (generation.terrain().version() != version || !generation.terrain().terrain().equals(plan.terrain()))
                throw new IllegalStateException("Saved generation and compiled plan disagree");
            var biomeRegistry = server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
            return new net.mystcraft.minecraft.age.generation.ClassicChunkGenerator(
                    server.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY),
                    generation.biomeSource(biomeRegistry, age.seed()), age.seed(), generation.terrain(), generation);
        }
        if ((version != 1 && version != 2) || !state.contains("GeneratorSpec", net.minecraft.nbt.Tag.TAG_COMPOUND))
            throw new IllegalStateException("Unknown or incomplete saved Age generator version " + version);
        var spec = net.mystcraft.minecraft.age.generation.ClassicTerrainSpec.fromTag(state.getCompound("GeneratorSpec"));
        if (spec.version() != version || !spec.terrain().equals(plan.terrain())) throw new IllegalStateException("Saved terrain and compiled plan disagree");
        var biomes = server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var biome = biomes.getHolderOrThrow(plan.biome() == null ? Biomes.PLAINS : ResourceKey.create(Registry.BIOME_REGISTRY, plan.biome()));
        return new net.mystcraft.minecraft.age.generation.ClassicChunkGenerator(
                server.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY),
                new FixedBiomeSource(biome), age.seed(), spec);
    }
    private static ChunkGenerator legacyGenerator(MinecraftServer server, AgeSymbolCompiler.Plan plan) {

        var registries = server.registryAccess();
        var biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
        var structures = registries.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        Holder<net.minecraft.world.level.biome.Biome> fixed = plan.biome() == null ? null
                : biomes.getHolderOrThrow(ResourceKey.create(Registry.BIOME_REGISTRY, plan.biome()));
        if (plan.terrain().equals("flat") || plan.terrain().equals("void")) {
            FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()), biomes);
            settings.setBiome(fixed != null ? fixed : biomes.getHolderOrThrow(Biomes.PLAINS));
            if (plan.terrain().equals("flat")) {
                settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
                settings.getLayersInfo().add(new FlatLayerInfo(123, Blocks.STONE));
                settings.getLayersInfo().add(new FlatLayerInfo(3, Blocks.DIRT));
                settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
                settings.setDecoration();
            }
            settings.updateLayers();
            return new FlatLevelSource(structures, settings);
        }
        ResourceKey<NoiseGeneratorSettings> settings = switch (plan.terrain()) {
            case "nether" -> NoiseGeneratorSettings.NETHER;
            case "end" -> NoiseGeneratorSettings.END;
            case "amplified" -> NoiseGeneratorSettings.AMPLIFIED;
            default -> NoiseGeneratorSettings.OVERWORLD;
        };
        BiomeSource source = fixed != null ? new FixedBiomeSource(fixed)
                : plan.terrain().equals("nether") ? MultiNoiseBiomeSource.Preset.NETHER.biomeSource(biomes)
                : plan.terrain().equals("end") ? new FixedBiomeSource(biomes.getHolderOrThrow(Biomes.THE_END))
                : MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(biomes);
        return new NoiseBasedChunkGenerator(structures, registries.registryOrThrow(Registry.NOISE_REGISTRY), source,
                registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY).getHolderOrThrow(settings));
    }
    private static void prepareArrival(ServerLevel level, AgeSavedData.Entry age, AgeSavedData saved) {
        level.getChunk(0, 0);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        int y = Math.max(64, Math.min(level.getMaxBuildHeight() - 5, surface + 2));
        BlockPos feet = new BlockPos(0, y, 0);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(feet.offset(x, -1, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
            for (int h = 0; h < 3; h++) level.setBlock(feet.offset(x, h, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setDefaultSpawnPos(feet, 0);
        age.state().putDouble("ArrivalX", 0.5); age.state().putDouble("ArrivalY", y); age.state().putDouble("ArrivalZ", 0.5);
        age.state().putBoolean("ArrivalReady", true); saved.setDirty();
    }
}
