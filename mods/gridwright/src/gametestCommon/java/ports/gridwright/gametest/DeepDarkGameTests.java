package ports.gridwright.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.gridwright.Gridwright;
import ports.gridwright.block.DeepDarkPortalBlock;
import ports.gridwright.deepdark.DeepDarkArrival;
import ports.gridwright.deepdark.DeepDarkReturnPoints;
import ports.gridwright.deepdark.DeepDarkReturnPoints.ReturnPoint;
import ports.gridwright.deepdark.DeepDarkTravel;
import ports.gridwright.registry.ModBlocks;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;

/**
 * The Deep Dark. GameTest servers build their world from built-in registries only, so datapack
 * dimensions are absent there: the datapack is loaded here into a private registry copy (proving every
 * reference resolves), and the portal logic is exercised in the test level itself.
 */
public class DeepDarkGameTests {

    private static final ResourceLocation DIMENSION = new ResourceLocation(Gridwright.MOD_ID, "deep_dark");
    private static final ResourceKey<Biome> CAVERNS = ResourceKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(Gridwright.MOD_ID, "deep_caverns"));

    /** Each Deep Dark ore feature and the vanilla placed feature whose per-chunk count it must beat. */
    private static final Map<String, String> ORE_BASELINES = Map.of(
            "ore_coal", "ore_coal_upper",
            "ore_iron", "ore_iron_middle",
            "ore_copper", "ore_copper",
            "ore_gold", "ore_gold",
            "ore_redstone", "ore_redstone",
            "ore_lapis", "ore_lapis",
            "ore_diamond", "ore_diamond");

    private record Datapack(MinecraftServer server, RegistryAccess.Writable registries, RegistryOps<JsonElement> ops) { }

    private static Datapack datapack;

    /** Every datapack worldgen registry, loaded once per server on top of a copy of the built-in ones. */
    private static synchronized Datapack datapack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        if (datapack == null || datapack.server() != server) {
            RegistryAccess.Writable registries = RegistryAccess.builtinCopy();
            RegistryOps<JsonElement> ops = RegistryOps.createAndLoad(JsonOps.INSTANCE, registries, server.getResourceManager());
            datapack = new Datapack(server, registries, ops);
        }
        return datapack;
    }

    private static JsonElement json(GameTestHelper helper, String path) {
        ResourceLocation id = new ResourceLocation(Gridwright.MOD_ID, path);
        Resource resource = helper.getLevel().getServer().getResourceManager().getResource(id)
                .orElseThrow(() -> new IllegalStateException("Missing resource " + id));
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + id, e);
        }
    }

    private static LevelStem stem(GameTestHelper helper) {
        Datapack pack = datapack(helper);
        DataResult<LevelStem> result = LevelStem.CODEC.parse(pack.ops(), json(helper, "dimension/deep_dark.json"));
        check(result.error().isEmpty(), "Deep Dark dimension does not decode: " + result.error().map(DataResult.PartialResult::message).orElse(""));
        return result.result().orElseThrow();
    }

    @GameTest(template = TEMPLATE)
    public static void dimensionResolves(GameTestHelper helper) {
        LevelStem stem = stem(helper);
        check(stem.typeHolder().isBound() && stem.typeHolder().is(DIMENSION), "Dimension type " + stem.typeHolder());
        check(stem.generator() instanceof NoiseBasedChunkGenerator, "Generator " + stem.generator());
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) stem.generator();
        check(generator.generatorSettings().isBound() && generator.generatorSettings().is(DIMENSION), "Noise settings " + generator.generatorSettings());
        check(generator.getBiomeSource().possibleBiomes().size() == 1
                && generator.getBiomeSource().possibleBiomes().stream().allMatch(biome -> biome.is(CAVERNS)), "Biomes " + generator.getBiomeSource().possibleBiomes());
        // Every reference the datapack made, in every worldgen registry, points at a real entry.
        datapack(helper).registries().registries().forEach(entry -> entry.value().holders().forEach(holder ->
                check(holder.isBound(), "Unbound " + entry.key().location() + " entry " + holder.key().location())));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dimensionTypeIsDark(GameTestHelper helper) {
        DimensionType type = stem(helper).typeHolder().value();
        check(!type.hasSkyLight() && type.hasCeiling() && type.ambientLight() == 0.0F, "Not dark and enclosed");
        check(type.monsterSpawnBlockLightLimit() == 0 && type.monsterSpawnLightTest().getMaxValue() == 0, "Monsters must need darkness");
        check(!type.bedWorks() && !type.respawnAnchorWorks(), "Respawning there must not work");
        check(!type.natural() && !type.piglinSafe() && !type.hasRaids() && !type.ultraWarm(), "Flags");
        check(type.coordinateScale() == 1.0 && type.minY() == 0 && type.height() == 128 && type.logicalHeight() == 128, "Size and scale");
        helper.succeed();
    }

    /** Sampled columns are solid at the bedrock floor and roof bands and have open caverns in between. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void terrainIsEnclosedCaves(GameTestHelper helper) {
        Datapack pack = datapack(helper);
        LevelStem stem = stem(helper);
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) stem.generator();
        DimensionType type = stem.typeHolder().value();
        LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());
        int columns = 0, air = 0, cave = 0, fluid = 0;
        for (long seed : new long[]{1L, 20261023L}) {
            RandomState random = RandomState.create(generator.generatorSettings().value(), pack.registries().registryOrThrow(Registry.NOISE_REGISTRY), seed);
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    int x = i * 53 - 260, z = j * 47 - 230;
                    NoiseColumn column = generator.getBaseColumn(x, z, height, random);
                    columns++;
                    for (int y = 0; y < 128; y++) {
                        BlockState state = column.getBlock(y);
                        if (!state.getFluidState().isEmpty()) fluid++;
                        boolean open = state.isAir();
                        if (y <= 5 || y >= 122) check(!open, "Hole in the bedrock band at " + x + "," + y + "," + z + " seed " + seed);
                        if (y >= 16 && y < 112) {
                            cave++;
                            if (open) air++;
                        }
                    }
                }
            }
        }
        double share = air / (double) cave;
        Gridwright.LOGGER.info("Deep Dark terrain sample: {} columns, {}% open between y 16 and 111, {} fluid blocks", columns, Math.round(share * 1000) / 10.0, fluid);
        check(share > 0.12 && share < 0.55, "Open share " + share + " outside 12%..55%");
        check(fluid == 0, "Base terrain must not flood: " + fluid + " fluid blocks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void cavernsSpawnOnlyMonsters(GameTestHelper helper) {
        Registry<Biome> biomes = datapack(helper).registries().registryOrThrow(Registry.BIOME_REGISTRY);
        Biome caverns = biomes.getOrThrow(CAVERNS);
        Biome plains = biomes.getOrThrow(Biomes.PLAINS);
        for (MobCategory category : MobCategory.values()) {
            if (category != MobCategory.MONSTER) {
                check(caverns.getMobSettings().getMobs(category).isEmpty(), "Non-hostile spawns in " + category.getName());
            }
        }
        List<MobSpawnSettings.SpawnerData> monsters = caverns.getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
        check(!monsters.isEmpty(), "No monsters");
        Map<EntityType<?>, Integer> vanilla = new HashMap<>();
        int vanillaTotal = 0, total = 0;
        for (MobSpawnSettings.SpawnerData data : plains.getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
            vanilla.merge(data.type, data.getWeight().asInt(), Integer::sum);
            vanillaTotal += data.getWeight().asInt();
        }
        for (MobSpawnSettings.SpawnerData data : monsters) {
            check(data.type.getCategory() == MobCategory.MONSTER, data.type + " is not a monster");
            Integer base = vanilla.get(data.type);
            check(base == null || data.getWeight().asInt() >= base, data.type + " weight below the overworld's");
            total += data.getWeight().asInt();
        }
        check(total > vanillaTotal, "Total monster weight " + total + " not above the overworld's " + vanillaTotal);
        check(caverns.getPrecipitation() == Biome.Precipitation.NONE, "Precipitation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void oresAreDenserThanVanilla(GameTestHelper helper) {
        RegistryAccess.Writable registries = datapack(helper).registries();
        Registry<PlacedFeature> placed = registries.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        Biome caverns = registries.registryOrThrow(Registry.BIOME_REGISTRY).getOrThrow(CAVERNS);
        var ores = caverns.getGenerationSettings().features().get(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal());
        Map<String, Integer> counts = new HashMap<>();
        ORE_BASELINES.forEach((ours, theirs) -> {
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation(Gridwright.MOD_ID, "deep_dark/" + ours));
            PlacedFeature feature = placed.getOrThrow(key);
            PlacedFeature baseline = placed.getOrThrow(ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation(theirs)));
            check(ores.stream().anyMatch(holder -> holder.is(key)), ours + " is not in the biome's ore step");
            check(feature.feature().unwrapKey().equals(baseline.feature().unwrapKey()), ours + " uses " + feature.feature().unwrapKey()
                    + " but vanilla " + theirs + " uses " + baseline.feature().unwrapKey());
            int count = count(feature), vanilla = count(baseline);
            check(count > vanilla && count <= 3 * vanilla, ours + " count " + count + " is not above vanilla's " + vanilla + " (and at most 3x)");
            counts.put(ours, count);
        });
        check(counts.get("ore_diamond") < counts.get("ore_iron"), "Diamonds must stay rarer than iron");
        helper.succeed();
    }

    private static int count(PlacedFeature feature) {
        for (PlacementModifier modifier : feature.placement()) {
            JsonElement json = PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, modifier).getOrThrow(false, message -> { });
            if (json.getAsJsonObject().get("type").getAsString().equals("minecraft:count")) {
                return IntProvider.CODEC.parse(JsonOps.INSTANCE, json.getAsJsonObject().get("count")).getOrThrow(false, message -> { }).getMaxValue();
            }
        }
        throw new IllegalStateException("No count placement in " + feature);
    }

    @GameTest(template = TEMPLATE)
    public static void biomeTagListsCaverns(GameTestHelper helper) {
        JsonElement tag = json(helper, "tags/worldgen/biome/is_deep_dark.json");
        check(tag.getAsJsonObject().getAsJsonArray("values").toString().contains(CAVERNS.location().toString()), "Tag " + tag);
        helper.succeed();
    }

    /** An existing pocket is used as it is, with one generated return portal beside the feet, reused next time. */
    @GameTest(template = TEMPLATE)
    public static void arrivalUsesOpenPocket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos feet = helper.absolutePos(new BlockPos(3, 2, 3));
        DeepDarkArrival.Arrival arrival = DeepDarkArrival.prepare(level, feet, 1, 1, feet.getY(), feet.getY() + 2);
        check(arrival != null && arrival.feet().equals(feet) && !arrival.carved(), "Arrival " + arrival);
        checkArrival(level, arrival);
        check(arrival.portal().equals(feet.north()), "Portal not beside the feet: " + arrival.portal());
        DeepDarkArrival.Arrival again = DeepDarkArrival.prepare(level, feet, 1, 1, feet.getY(), feet.getY() + 2);
        check(again != null && again.portal().equals(arrival.portal()), "Second arrival made another portal: " + again);
        check(portals(helper) == 1, "Expected one portal, found " + portals(helper));
        helper.succeed();
    }

    /** A portal behind a wall is not "in reach": a new one is placed beside the arriving player. */
    @GameTest(template = TEMPLATE)
    public static void arrivalIgnoresPortalBehindWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int z = 1; z <= 5; z++) for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.DEEP_DARK_PORTAL.get());
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 3));
        DeepDarkArrival.Arrival arrival = DeepDarkArrival.prepare(level, feet, 0, 0, feet.getY(), feet.getY());
        check(arrival != null && arrival.feet().equals(feet), "Arrival " + arrival);
        checkArrival(level, arrival);
        check(arrival.portal().equals(feet.north()), "Reused a portal behind a wall: " + arrival.portal());
        check(portals(helper) == 2, "Expected the walled-off portal plus a new one, found " + portals(helper));
        helper.succeed();
    }

    /** Arrivals keep the whole 3x3 room inside the world border; vanilla's box check only tests overlap. */
    @GameTest(template = TEMPLATE)
    public static void arrivalStaysInsideWorldBorder(GameTestHelper helper) {
        WorldBorder border = new WorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(20.0);   // x and z from -10 to 10
        check(DeepDarkArrival.withinBorder(border, new BlockPos(0, 64, 0)), "Centre rejected");
        check(DeepDarkArrival.withinBorder(border, new BlockPos(8, 64, -9)), "Room touching the edge rejected");
        check(!DeepDarkArrival.withinBorder(border, new BlockPos(9, 64, 0)), "Room crossing the edge accepted");
        check(!DeepDarkArrival.withinBorder(border, new BlockPos(0, 64, -10)), "Room crossing the edge accepted");
        check(!DeepDarkArrival.withinBorder(border, new BlockPos(40, 64, 40)), "Room outside accepted");
        check(!DeepDarkArrival.contains(border, new AABB(9.5, 0, 0, 10.5, 2, 1)), "Overlapping box counted as inside");
        // Surface fallbacks use the nearest column wholly inside the border.
        check(DeepDarkTravel.columnInside(border, 3, -4).equals(new BlockPos(3, 0, -4)), "Inside column moved");
        check(DeepDarkTravel.columnInside(border, 40, -40).equals(new BlockPos(9, 0, -10)), "Outside column not clamped");
        check(DeepDarkTravel.columnInside(border, 10, 10).equals(new BlockPos(9, 0, 9)), "Edge column not clamped");
        WorldBorder tiny = new WorldBorder();
        tiny.setCenter(100.5, -50.5);
        tiny.setSize(0.5);
        check(DeepDarkTravel.columnInside(tiny, 0, 0).equals(new BlockPos(100, 0, -51)), "Tiny border not centred");
        helper.succeed();
    }

    /** Solid rock is dug out, but bedrock and block entities stay and lava and gravel are sealed off. */
    @GameTest(template = TEMPLATE)
    public static void arrivalCarvesSafely(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(6, 7, 6))) helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.BEDROCK);    // the floor under the feet
        helper.setBlock(new BlockPos(4, 5, 4), Blocks.BEDROCK);    // a corner of the room
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.CHEST);      // a block entity inside the room
        helper.setBlock(new BlockPos(5, 3, 3), Blocks.LAVA);       // lava in the wall
        helper.setBlock(new BlockPos(3, 6, 3), Blocks.GRAVEL);     // gravel over the room
        BlockPos feet = helper.absolutePos(new BlockPos(3, 3, 3));
        DeepDarkArrival.Arrival arrival = DeepDarkArrival.prepare(level, feet, 0, 0, feet.getY(), feet.getY());
        check(arrival != null && arrival.carved() && arrival.feet().equals(feet), "Arrival " + arrival);
        checkArrival(level, arrival);
        helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 2, 3));
        helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(4, 5, 4));
        helper.assertBlockPresent(Blocks.CHEST, new BlockPos(2, 4, 2));
        check(helper.getBlockEntity(new BlockPos(2, 4, 2)) != null, "Chest lost its block entity");
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 3, 3));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 6, 3));
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -2, -2), feet.offset(2, 3, 2))) {
            check(level.getFluidState(pos).isEmpty(), "Fluid left at " + pos);
        }
        helper.succeed();
    }

    /** Where nothing may be removed there is no arrival, and nothing is changed. */
    @GameTest(template = TEMPLATE)
    public static void arrivalNeverBreaksBedrock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 5, 5))) helper.setBlock(pos, Blocks.BEDROCK);
        BlockPos feet = helper.absolutePos(new BlockPos(3, 3, 3));
        check(DeepDarkArrival.prepare(level, feet, 1, 1, feet.getY() - 1, feet.getY() + 1) == null, "Arrived inside bedrock");
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 5, 5))) helper.assertBlockPresent(Blocks.BEDROCK, pos);
        helper.succeed();
    }

    private static void checkArrival(ServerLevel level, DeepDarkArrival.Arrival arrival) {
        BlockPos feet = arrival.feet();
        check(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir(), "No two-high space at " + feet);
        check(level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP), "No solid floor under " + feet);
        check(DeepDarkArrival.standable(level, feet), "Arrival not standable");
        BlockState portal = level.getBlockState(arrival.portal());
        check(portal.is(ModBlocks.DEEP_DARK_PORTAL.get()) && portal.getValue(DeepDarkPortalBlock.GENERATED), "No generated return portal at " + arrival.portal());
        check(arrival.portal().distManhattan(feet) <= 4, "Return portal out of reach");
    }

    private static int portals(GameTestHelper helper) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(7, 7, 7))) {
            if (helper.getBlockState(pos).is(ModBlocks.DEEP_DARK_PORTAL.get())) found++;
        }
        return found;
    }

    @GameTest(template = TEMPLATE)
    public static void returnPointsRoundTrip(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        ReturnPoint point = new ReturnPoint(Level.NETHER, new Vec3(12.5, 70.0, -3.25), 90.0F, -15.0F);
        DeepDarkReturnPoints data = new DeepDarkReturnPoints();
        data.put(player, point);
        check(data.isDirty(), "Not marked dirty");
        CompoundTag saved = data.save(new CompoundTag());
        CompoundTag broken = new CompoundTag();
        broken.putUUID("Player", UUID.randomUUID());
        broken.putString("Dimension", "Not A Valid Id");
        ((ListTag) saved.get("Points")).add(broken);
        DeepDarkReturnPoints loaded = DeepDarkReturnPoints.load(saved);
        check(loaded.all().size() == 1 && point.equals(loaded.get(player)), "Round trip lost data: " + loaded.all());
        loaded.remove(player);
        check(loaded.get(player) == null, "Not removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void returnFallsBackToOverworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        check(level == server.overworld(), "GameTests are expected to run in the overworld");
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        ServerPlayer player = GameTestSupport.player(helper, Owner.random("gwD"));
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
        player.moveTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);

        DeepDarkTravel.Destination recorded = DeepDarkTravel.returnDestination(player, new ReturnPoint(Level.OVERWORLD, spot, 45.0F, 10.0F));
        check(recorded.level() == level && recorded.position().equals(spot) && recorded.yRot() == 45.0F, "Recorded spot not used: " + recorded);

        Vec3 surface = DeepDarkTravel.surface(level, player.getBlockX(), player.getBlockZ());
        ReturnPoint gone = new ReturnPoint(ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(Gridwright.MOD_ID, "removed")), spot, 0.0F, 0.0F);
        for (ReturnPoint point : new ReturnPoint[]{gone, null, new ReturnPoint(DeepDarkTravel.DEEP_DARK, spot, 0.0F, 0.0F)}) {
            DeepDarkTravel.Destination fallback = DeepDarkTravel.returnDestination(player, point);
            check(fallback.level() == server.overworld(), "Fallback not in the overworld for " + point);
            check(surface != null && fallback.position().equals(surface), "Fallback " + fallback.position() + " is not the surface " + surface);
        }

        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        DeepDarkTravel.Destination moved = DeepDarkTravel.returnDestination(player, new ReturnPoint(Level.OVERWORLD, spot, 0.0F, 0.0F));
        check(moved.level() == level && !moved.position().equals(spot) && DeepDarkTravel.clear(level, player, moved.position()),
                "Blocked spot not replaced by a clear one: " + moved.position());
        helper.succeed();
    }

    /** Return spots and surface fallbacks never put a player on or in something that hurts. */
    @GameTest(template = TEMPLATE)
    public static void returnAvoidsHarmfulBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        check(DeepDarkTravel.clear(level, null, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)))), "Stone floor rejected");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.CACTUS);
        check(!DeepDarkTravel.clear(level, null, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)))), "Cactus floor accepted");
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.SWEET_BERRY_BUSH);
        check(!DeepDarkTravel.clear(level, null, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)))), "Berry bush accepted");
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.WATER);
        check(DeepDarkTravel.clear(level, null, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 4)))), "Water surface rejected");
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.AIR);    // the floor is gone and there is air below it
        check(!DeepDarkTravel.clear(level, null, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 2)))), "Unsupported spot accepted");
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.LADDER);
        check(DeepDarkTravel.clear(level, null, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 2)))), "Ladder spot rejected");

        Vec3 midJump = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 3))).add(0.0, 0.4, 0.0);
        check(DeepDarkTravel.clear(level, null, midJump), "Mid-jump spot over stone rejected");
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.MAGMA_BLOCK);
        check(!DeepDarkTravel.clear(level, null, midJump), "Mid-jump spot over magma accepted");

        helper.setBlock(new BlockPos(3, 1, 3), Blocks.MAGMA_BLOCK);
        BlockPos magmaTop = helper.absolutePos(new BlockPos(3, 2, 3));
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, magmaTop.getX(), magmaTop.getZ()) == magmaTop.getY()) {
            Vec3 surface = DeepDarkTravel.surface(level, magmaTop.getX(), magmaTop.getZ());
            check(surface != null && !surface.equals(Vec3.atBottomCenterOf(magmaTop)), "Surface on magma: " + surface);
            BlockPos floor = new BlockPos(surface).below();
            check(!DeepDarkArrival.harmful(level.getBlockState(floor)) && DeepDarkTravel.clear(level, null, surface), "Unsafe surface " + surface);
        } else {
            Gridwright.LOGGER.info("returnAvoidsHarmfulBlocks: something above the test area, surface part skipped");
        }
        helper.succeed();
    }

    /** The last fallback is a standable spot near spawn; with none, there is no destination at all. */
    @GameTest(template = TEMPLATE)
    public static void spawnFallbackNeedsAStandableSpot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos spawn = helper.absolutePos(new BlockPos(3, 1, 3));
        Vec3 found = DeepDarkTravel.nearSpawn(level, spawn);
        check(Vec3.atBottomCenterOf(spawn).equals(found), "Standable spawn not used: " + found);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(7, 7, 7))) helper.setBlock(pos, Blocks.MAGMA_BLOCK);
        check(DeepDarkTravel.nearSpawn(level, spawn) == null, "Unsafe spawn accepted");
        helper.succeed();
    }

    /**
     * Without the Deep Dark (a datapack removed it, or a GameTest world built from built-in registries)
     * the portal does nothing and never throws. A player the server does not list (a fake player) never
     * travels either, whether or not the dimension exists.
     */
    @GameTest(template = TEMPLATE)
    public static void portalWithoutDimensionDoesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos portal = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.DEEP_DARK_PORTAL.get());
        ServerPlayer player = GameTestSupport.player(helper, Owner.random("gwE"));
        Vec3 start = Vec3.atBottomCenterOf(portal.east());
        player.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        check(DeepDarkTravel.use(player, null) == DeepDarkTravel.Outcome.NO_DIMENSION, "Outcome without the dimension");
        DeepDarkTravel.Outcome live = DeepDarkTravel.use(player);
        check(live == (level.getServer().getLevel(DeepDarkTravel.DEEP_DARK) == null ? DeepDarkTravel.Outcome.NO_DIMENSION
                : DeepDarkTravel.Outcome.NOT_A_PLAYER), "Outcome for an unlisted player: " + live);
        BlockState state = level.getBlockState(portal);
        InteractionResult result = state.use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(portal), Direction.EAST, portal, false));
        check(result == InteractionResult.CONSUME, "Use result " + result);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(player.level == level && player.position().equals(start), "Player moved");
                    check(DeepDarkReturnPoints.get(level.getServer()).get(player.getUUID()) == null, "A return point was recorded");
                })
                .thenSucceed();
    }

    /**
     * Where the server has really generated the Deep Dark (the Forge GameTest server loads datapack
     * dimensions; Fabric's builds its world from built-in registries and has none), arrival works in the
     * real terrain: a safe spot between the bedrock floor and roof, with a return portal.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void arrivalInGeneratedDeepDark(GameTestHelper helper) {
        ServerLevel deepDark = helper.getLevel().getServer().getLevel(DeepDarkTravel.DEEP_DARK);
        if (deepDark == null) {
            Gridwright.LOGGER.info("This GameTest server has no Deep Dark level; the datapack is checked by the decode tests instead");
            helper.succeed();
            return;
        }
        check(!deepDark.dimensionType().hasSkyLight() && deepDark.dimensionType().hasCeiling(), "Live dimension type");
        for (BlockPos requested : new BlockPos[]{new BlockPos(0, 64, 0), new BlockPos(1200, -40, -900), new BlockPos(-700, 300, 2100)}) {
            DeepDarkArrival.Arrival arrival = DeepDarkArrival.prepare(deepDark, requested);
            check(arrival != null, "No arrival near " + requested);
            checkArrival(deepDark, arrival);
            int y = arrival.feet().getY();
            check(y >= deepDark.getMinBuildHeight() + DeepDarkArrival.FLOOR_MARGIN && y <= deepDark.getMaxBuildHeight() - DeepDarkArrival.ROOF_MARGIN,
                    "Arrival height " + y);
            BlockPos column = arrival.feet();
            check(deepDark.getBlockState(column.atY(deepDark.getMinBuildHeight())).is(Blocks.BEDROCK), "No bedrock floor under " + column);
            check(deepDark.getBlockState(column.atY(deepDark.getMaxBuildHeight() - 1)).is(Blocks.BEDROCK), "No bedrock roof over " + column);
        }
        helper.succeed();
    }

    /** A crafted portal drops itself; a generated return portal drops nothing, so trips cannot mint portals. */
    @GameTest(template = TEMPLATE)
    public static void generatedPortalsDropNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState crafted = ModBlocks.DEEP_DARK_PORTAL.get().defaultBlockState();
        List<ItemStack> drops = Block.getDrops(crafted, level, pos, null);
        check(drops.size() == 1 && drops.get(0).is(ModBlocks.DEEP_DARK_PORTAL.get().asItem()) && drops.get(0).getCount() == 1, "Crafted drops " + drops);
        check(Block.getDrops(crafted.setValue(DeepDarkPortalBlock.GENERATED, true), level, pos, null).isEmpty(), "Generated portal dropped something");
        check(crafted.getLightEmission() > 0, "The portal should glow");
        helper.succeed();
    }
}
