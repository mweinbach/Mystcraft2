package ports.jurassicraft.worldgen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

/** Shared fossil-budget checks. Both loaders call these from their GameTest mods. */
public final class FossilGenerationChecks {
    private FossilGenerationChecks() {}

    public static void sharedBudget(GameTestHelper helper) {
        require(helper, FossilDepositFeature.periodSpecies("cretaceous").equals(List.of(Species.VELOCIRAPTOR, Species.MICRORAPTOR,
                Species.TRICERATOPS, Species.TYRANNOSAURUS, Species.GALLIMIMUS, Species.PARASAUROLOPHUS)),
                "cretaceous species order must follow EntityHandler's HashMap");
        require(helper, FossilDepositFeature.periodSpecies("jurassic").equals(List.of(Species.BRACHIOSAURUS, Species.DILOPHOSAURUS)),
                "jurassic species order must follow EntityHandler's HashMap");
        require(helper, FossilDepositFeature.periodSpecies("triassic").equals(List.of(Species.MUSSAURUS)), "triassic species missing");
        require(helper, FossilDepositFeature.periodSpecies("devonian").equals(List.of(Species.COELACANTH)), "devonian species missing");
        require(helper, FossilDepositFeature.yLevel(0.0F) == 64 && FossilDepositFeature.yLevel(145.5F) == 47
                        && FossilDepositFeature.yLevel(66.1F) == 57, "time-period Y truncation diverged");

        ScriptedRandom random = scriptedChunk();
        List<FossilDepositFeature.Attempt> attempts = FossilDepositFeature.plan(random, 16, -32);
        random.assertExhausted();
        require(helper, attempts.equals(List.of(
                new FossilDepositFeature.Attempt(Species.VELOCIRAPTOR, 17, 50, -30),
                new FossilDepositFeature.Attempt(Species.DILOPHOSAURUS, 16, 38, -32),
                new FossilDepositFeature.Attempt(Species.MUSSAURUS, 21, 39, -29),
                new FossilDepositFeature.Attempt(Species.COELACANTH, 31, 13, -24)
        )), "shared 32-attempt loop did not replay upstream's species, period and depth rolls");

        final int chunks = 4000;
        Map<Species, Integer> totals = new EnumMap<>(Species.class);
        Map<Species, int[]> heights = new EnumMap<>(Species.class);
        for (Species species : Species.values()) {
            totals.put(species, 0);
            int[] range = band(species);
            heights.put(species, new int[range[1] + 3 - (range[0] - 4) + 1]);
        }
        int attemptTotal = 0;
        for (int chunk = 0; chunk < chunks; chunk++) {
            List<FossilDepositFeature.Attempt> planned = FossilDepositFeature.plan(RandomSource.create(0xC0FFEEL + chunk), chunk * 16, -chunk * 16);
            require(helper, planned.size() <= FossilDepositFeature.ATTEMPTS, "chunk exceeded the shared 32-attempt budget");
            attemptTotal += planned.size();
            for (FossilDepositFeature.Attempt attempt : planned) {
                int[] range = band(attempt.species());
                int minY = range[0] - 4, maxY = range[1] + 3;
                require(helper, attempt.y() >= minY && attempt.y() <= maxY, "fossil height outside upstream bounds for " + attempt.species());
                heights.get(attempt.species())[attempt.y() - minY]++;
                totals.put(attempt.species(), totals.get(attempt.species()) + 1);
            }
        }
        double mean = (double) attemptTotal / chunks;
        require(helper, Math.abs(mean - 12.5D) < 0.5D, "shared fossil attempts have the wrong per-chunk mean: " + mean);
        for (Species species : Species.values()) {
            int[] range = band(species);
            int band = range[1] - range[0] + 1;
            int periodSpecies = FossilDepositFeature.periodSpecies(species.period()).size();
            double probability = band / (64.0D * periodSpecies);
            double expected = FossilDepositFeature.ATTEMPTS * probability;
            double tolerance = 8 * Math.sqrt(FossilDepositFeature.ATTEMPTS * probability * (1 - probability) / chunks) + 0.02D;
            double actual = (double) totals.get(species) / chunks;
            require(helper, Math.abs(actual - expected) < tolerance, "wrong shared-budget rate for " + species);
            int samples = totals.get(species);
            int minY = range[0] - 4;
            for (int y = minY; y <= range[1] + 3; y++) {
                int combinations = 0;
                for (int base = range[0]; base <= range[1]; base++)
                    if (y - base >= -4 && y - base <= 3) combinations++;
                double chance = combinations / (8.0D * band);
                double heightTolerance = 8 * Math.sqrt(samples * chance * (1 - chance)) + 8;
                require(helper, Math.abs(heights.get(species)[y - minY] - samples * chance) < heightTolerance,
                        "wrong fossil height distribution at Y=" + y + " for " + species);
            }
        }
        requireRegistered(helper);
        helper.succeed();
    }

    public static void veinReplacesOnlyStone(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos marker = helper.absolutePos(new BlockPos(1, 2, 1));
        var chunk = level.getChunk(marker).getPos();
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        int y0 = marker.getY();
        BlockPos origin = new BlockPos(x0, y0, z0);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x <= 16; x++) for (int y = -4; y <= 6; y++) for (int z = 0; z <= 16; z++)
            level.setBlock(cursor.set(x0 + x, y0 + y, z0 + z), Blocks.STONE.defaultBlockState(), 2);
        BlockPos deepslate = new BlockPos(x0 + 8, y0, z0 + 8);
        BlockPos bedrock = deepslate.above();
        BlockPos iron = deepslate.east();
        BlockPos chest = deepslate.south();
        level.setBlock(deepslate, Blocks.DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 2);
        level.setBlock(iron, Blocks.IRON_ORE.defaultBlockState(), 2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        RandomSource placed = RandomSource.create(12345L);
        RandomSource twin = RandomSource.create(12345L);
        BlockState fossil = JurassicContent.FOSSILS.get(Species.VELOCIRAPTOR).get().defaultBlockState();
        FossilDepositFeature.placeVein(level, placed, origin, fossil);
        twin.nextFloat();
        twin.nextInt(3);
        twin.nextInt(3);
        for (int i = 0; i < 5; i++) twin.nextDouble();
        require(helper, placed.nextInt() == twin.nextInt(), "vein random draws diverged from WorldGenMinable");
        require(helper, level.getBlockState(deepslate).is(Blocks.DEEPSLATE), "deepslate replaced");
        require(helper, level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock replaced");
        require(helper, level.getBlockState(iron).is(Blocks.IRON_ORE), "iron ore replaced");
        require(helper, level.getBlockState(chest).is(Blocks.CHEST), "chest replaced");
        int fossils = 0;
        for (int x = 0; x <= 16; x++) for (int y = -4; y <= 6; y++) for (int z = 0; z <= 16; z++) {
            BlockState state = level.getBlockState(cursor.set(x0 + x, y0 + y, z0 + z));
            if (state.is(fossil.getBlock())) fossils++;
            else require(helper, state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.BEDROCK)
                    || state.is(Blocks.IRON_ORE) || state.is(Blocks.CHEST), "vein wrote an unexpected block");
        }
        require(helper, fossils > 0, "vein replaced no stone");
        helper.succeed();
    }

    private static void requireRegistered(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var configured = access.registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY);
        var placed = access.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, "fossil_deposits");
        ConfiguredFeature<?, ?> feature = configured.get(id);
        PlacedFeature placedFeature = placed.get(id);
        require(helper, feature != null && feature.feature() instanceof FossilDepositFeature, "fossil_deposits feature missing");
        require(helper, placedFeature != null && placedFeature.feature().value() == feature && placedFeature.placement().isEmpty(),
                "fossil_deposits placement must be empty so the attempt loop owns the random");
        for (Species species : Species.values())
            require(helper, placed.get(new ResourceLocation(PortMod.MOD_ID, species.id() + "_fossil_block")) == null,
                    "per-species fossil feature still registered for " + species);
        var plains = access.registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS).value();
        int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        int found = 0;
        var steps = plains.getGenerationSettings().features();
        for (int i = 0; i < steps.size(); i++) {
            int matches = (int) steps.get(i).stream()
                    .filter(holder -> holder.unwrapKey().map(key -> key.location().equals(id)).orElse(false)).count();
            if (i == step) found = matches;
            else require(helper, matches == 0, "fossil_deposits added outside underground ores");
        }
        require(helper, found == 1, "plains must contain fossil_deposits once");
        for (String name : new String[] {"fossil_nest", "fossil_trackway"}) {
            ResourceLocation other = new ResourceLocation(PortMod.MOD_ID, name);
            require(helper, configured.get(other) != null && placed.get(other) != null, "missing " + name);
        }
    }

    /** Inclusive unoffset Y band, from {@code TimePeriod}'s strict bounds. */
    private static int[] band(Species species) {
        return switch (species.period()) {
            case "cretaceous" -> new int[] {48, 56};
            case "jurassic" -> new int[] {42, 46};
            case "triassic" -> new int[] {36, 40};
            case "devonian" -> new int[] {16, 21};
            default -> throw new IllegalStateException(species.period());
        };
    }

    private static ScriptedRandom scriptedChunk() {
        List<Integer> bounds = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<Float> floats = new ArrayList<>();
        List<Double> doubles = new ArrayList<>();
        class Adds {
            void nextInt(int bound, int value) { bounds.add(bound); values.add(value); }
            void vein() {
                floats.add(0.25F);
                nextInt(3, 1);
                nextInt(3, 2);
                for (int i = 0; i < 5; i++) doubles.add(0.5D);
            }
            void column(int x, int y, int z) { nextInt(16, x); nextInt(64, y); nextInt(16, z); }
        }
        Adds add = new Adds();
        add.column(1, 50, 2); add.nextInt(8, 4); add.nextInt(6, 0); add.vein();
        add.column(0, 42, 0); add.nextInt(8, 0); add.nextInt(2, 1); add.vein();
        add.column(5, 36, 3); add.nextInt(8, 7); add.nextInt(1, 0); add.vein();
        add.column(15, 16, 8); add.nextInt(8, 1); add.nextInt(1, 0); add.vein();
        add.column(4, 63, 4); add.nextInt(8, 3);
        add.column(0, 0, 0);
        add.column(1, 7, 1);
        add.column(2, 57, 2);
        for (int i = 0; i < 24; i++) add.column(0, 0, 0);
        return new ScriptedRandom(bounds.stream().mapToInt(Integer::intValue).toArray(),
                values.stream().mapToInt(Integer::intValue).toArray(),
                floats, doubles);
    }

    private static void require(GameTestHelper helper, boolean value, String message) {
        if (!value) helper.fail(message);
    }

    /** Fails if the 32-attempt loop draws a different sequence than upstream. */
    private static final class ScriptedRandom implements RandomSource {
        private final int[] bounds;
        private final int[] values;
        private int ints;
        private final float[] floats;
        private int floatIndex;
        private final double[] doubles;
        private int doubleIndex;

        ScriptedRandom(int[] bounds, int[] values, List<Float> floats, List<Double> doubles) {
            this.bounds = bounds;
            this.values = values;
            this.floats = new float[floats.size()];
            for (int i = 0; i < floats.size(); i++) this.floats[i] = floats.get(i);
            this.doubles = doubles.stream().mapToDouble(Double::doubleValue).toArray();
        }

        void assertExhausted() {
            if (ints != bounds.length || floatIndex != floats.length || doubleIndex != doubles.length)
                throw new IllegalStateException("unused fossil random draws");
        }

        @Override public int nextInt(int bound) {
            if (ints >= bounds.length || bounds[ints] != bound)
                throw new IllegalStateException("unexpected nextInt(" + bound + ") at draw " + ints);
            return values[ints++];
        }
        @Override public float nextFloat() {
            if (floatIndex >= floats.length) throw new IllegalStateException("unexpected nextFloat");
            return floats[floatIndex++];
        }
        @Override public double nextDouble() {
            if (doubleIndex >= doubles.length) throw new IllegalStateException("unexpected nextDouble");
            return doubles[doubleIndex++];
        }
        @Override public RandomSource fork() { throw new UnsupportedOperationException(); }
        @Override public PositionalRandomFactory forkPositional() { throw new UnsupportedOperationException(); }
        @Override public void setSeed(long seed) { throw new UnsupportedOperationException(); }
        @Override public int nextInt() { throw new UnsupportedOperationException(); }
        @Override public long nextLong() { throw new UnsupportedOperationException(); }
        @Override public boolean nextBoolean() { throw new UnsupportedOperationException(); }
        @Override public double nextGaussian() { throw new UnsupportedOperationException(); }
    }
}
