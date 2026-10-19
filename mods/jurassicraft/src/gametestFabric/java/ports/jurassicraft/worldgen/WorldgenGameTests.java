package ports.jurassicraft.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.egg.FossilNestRegistry;
import java.util.Optional;

public final class WorldgenGameTests implements FabricGameTest {
    public WorldgenGameTests() {}
    private static void require(GameTestHelper helper, boolean value, String message) { if (!value) helper.fail(message); }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void fossilWorldgenDataLoadsInPlains(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var configured = registries.registryOrThrow(net.minecraft.core.Registry.CONFIGURED_FEATURE_REGISTRY);
        var placed = registries.registryOrThrow(net.minecraft.core.Registry.PLACED_FEATURE_REGISTRY);
        var biomes = registries.registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY);
        var plains = biomes.get(net.minecraft.world.level.biome.Biomes.PLAINS);
        require(helper, plains != null, "plains biome missing");
        var random = net.minecraft.util.RandomSource.create(1);
        for (var species : ports.jurassicraft.content.Species.values()) {
            var id = new net.minecraft.resources.ResourceLocation(PortMod.MOD_ID, species.id() + "_fossil_block");
            var feature = configured.get(id);
            var placedFeature = placed.get(id);
            require(helper, feature != null && placedFeature != null, "missing fossil worldgen " + id);
            require(helper, placedFeature.feature().value() == feature, "placed fossil must reference its configured feature " + id);
            require(helper, feature.config() instanceof net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration, "fossil must use an ore configuration " + id);
            var ore = (net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration) feature.config();
            var expected = ports.jurassicraft.content.JurassicContent.FOSSILS.get(species).get();
            for (var state : new net.minecraft.world.level.block.state.BlockState[] {Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()}) {
                require(helper, ore.targetStates.stream().anyMatch(target -> target.state.is(expected) && target.target.test(state, random)),
                        "fossil must replace stone and deepslate with its own block " + id);
            }
            require(helper, ore.targetStates.stream().noneMatch(target -> target.target.test(Blocks.BEDROCK.defaultBlockState(), random)), "fossil must not replace bedrock " + id);
            if (ports.jurassicraft.config.JurassicConfig.worldgenEnabled(ports.jurassicraft.worldgen.WorldgenCategory.FOSSILS))
                require(helper, plains.getGenerationSettings().hasFeature(placedFeature), "fossil feature missing from overworld biome " + id);
        }
        for (String name : new String[] {"fossil_nest", "fossil_trackway"}) {
            var id = new net.minecraft.resources.ResourceLocation(PortMod.MOD_ID, name);
            var feature = configured.get(id);
            var placedFeature = placed.get(id);
            require(helper, feature != null && placedFeature != null && placedFeature.feature().value() == feature,
                    "configured and placed features must load " + id);
            if (ports.jurassicraft.config.JurassicConfig.worldgenEnabled(ports.jurassicraft.worldgen.WorldgenCategory.forFeature(id)))
                require(helper, plains.getGenerationSettings().hasFeature(placedFeature), "underground feature missing from overworld " + id);
        }
        helper.succeed();
    }

    /** Upstream rolls 32 Y levels, requires strict period bounds, then adds -4..+3. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void fossilAttemptAndHeightDistributions(GameTestHelper helper) {
        final int samples = 6000;
        var placed = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.Registry.PLACED_FEATURE_REGISTRY);
        var context = new PlacementContext(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(), Optional.empty());
        require(helper, new BinomialCount(32, 0.0F).sample(RandomSource.create(1)) == 0
                        && new BinomialCount(32, 1.0F).sample(RandomSource.create(1)) == 32,
                "binomial count must respect zero and certain probabilities");
        for (Species species : Species.values()) {
            // TimePeriod's start/end are strict. These are the selected, unoffset Y ranges and
            // the number of enabled, non-hybrid upstream species for each period.
            int first, last, periodSpecies;
            switch (species.period()) {
                case "cretaceous" -> { first = 48; last = 56; periodSpecies = 6; }
                case "jurassic" -> { first = 42; last = 46; periodSpecies = 2; }
                case "triassic" -> { first = 36; last = 40; periodSpecies = 1; }
                case "devonian" -> { first = 16; last = 21; periodSpecies = 1; }
                default -> throw new IllegalStateException("unexpected fossil period " + species.period());
            }
            var id = new net.minecraft.resources.ResourceLocation(PortMod.MOD_ID, species.id() + "_fossil_block");
            var feature = placed.get(id);
            require(helper, feature != null && feature.placement().size() >= 3
                            && feature.placement().get(0) instanceof CountPlacement
                            && feature.placement().get(2) instanceof HeightRangePlacement,
                    "missing count or height placement for " + id);
            var count = (CountPlacement) feature.placement().get(0);
            var height = (HeightRangePlacement) feature.placement().get(2);
            RandomSource countRandom = RandomSource.create(19381L + species.legacyId());
            RandomSource heightRandom = RandomSource.create(84197L + species.legacyId());
            int totalAttempts = 0, emptyChunks = 0;
            int minY = first - 4, maxY = last + 3;
            int[] heights = new int[maxY - minY + 1];
            for (int i = 0; i < samples; i++) {
                int attempts = (int) count.getPositions(context, countRandom, BlockPos.ZERO).count();
                require(helper, attempts >= 0 && attempts <= 32, "attempt count outside upstream bounds for " + id);
                totalAttempts += attempts;
                if (attempts == 0) emptyChunks++;
                int y = height.getPositions(context, heightRandom, BlockPos.ZERO).findFirst().orElseThrow().getY();
                require(helper, y >= minY && y <= maxY, "fossil height outside upstream bounds for " + id);
                heights[y - minY]++;
            }
            double probability = (last - first + 1) / (64.0 * periodSpecies);
            double expectedMean = 32 * probability;
            double expectedEmpty = Math.pow(1 - probability, 32);
            double meanTolerance = 6 * Math.sqrt(32 * probability * (1 - probability) / samples) + .02;
            double emptyTolerance = 6 * Math.sqrt(expectedEmpty * (1 - expectedEmpty) / samples) + .01;
            require(helper, Math.abs((double) totalAttempts / samples - expectedMean) < meanTolerance,
                    "fossil attempts have wrong mean for " + id);
            require(helper, Math.abs((double) emptyChunks / samples - expectedEmpty) < emptyTolerance,
                    "fossil attempts have wrong empty-chunk frequency for " + id);
            for (int y = minY; y <= maxY; y++) {
                int combinations = 0;
                for (int base = first; base <= last; base++)
                    if (y - base >= -4 && y - base <= 3) combinations++;
                double chance = combinations / (8.0 * (last - first + 1));
                double tolerance = 7 * Math.sqrt(samples * chance * (1 - chance)) + 8;
                require(helper, Math.abs(heights[y - minY] - samples * chance) < tolerance,
                        "wrong fossil height distribution at Y=" + y + " for " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void trackwayReplacesOnlyStone(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = new BlockPos(0, 1, 1);
        helper.setBlock(start, Blocks.STONE);
        helper.setBlock(start.east(), Blocks.BEDROCK);
        helper.setBlock(start.east(2), Blocks.CHEST);
        var track = FossilNestRegistry.trackway(1).get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        require(helper, FossilTrackwayFeature.placeTrack(level, helper.absolutePos(start), Direction.EAST, track, 3), "stone not replaced");
        require(helper, helper.getBlockState(start).equals(track), "track type or direction changed");
        require(helper, helper.getBlockState(start.east()).is(Blocks.BEDROCK), "bedrock replaced");
        require(helper, helper.getBlockState(start.east(2)).is(Blocks.CHEST), "block entity replaced");
        helper.succeed();
    }
}
