package ports.jurassicraft.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

/**
 * One shared budget of 32 fossil attempts per chunk, in upstream's order.
 * Each attempt samples a column, keeps the first time period whose strict Y bounds contain it,
 * shifts that Y by {@code -4..+3}, then picks one registered species from the period.
 * Species order is {@code EntityHandler}'s HashMap iteration, which is what
 * {@code getDinosaursFromPeriod} returns. The vein is the 1.12 {@code WorldGenMinable} of size 5
 * that replaces stone only.
 */
public final class FossilDepositFeature extends Feature<NoneFeatureConfiguration> {
    static final int ATTEMPTS = 32;
    private static final int VEIN_SIZE = 5;
    private static final List<Species> CRETACEOUS = List.of(Species.VELOCIRAPTOR, Species.MICRORAPTOR,
            Species.TRICERATOPS, Species.TYRANNOSAURUS, Species.GALLIMIMUS, Species.PARASAUROLOPHUS);
    private static final List<Species> JURASSIC = List.of(Species.BRACHIOSAURUS, Species.DILOPHOSAURUS);
    private static final List<Species> TRIASSIC = List.of(Species.MUSSAURUS);
    private static final List<Species> DEVONIAN = List.of(Species.COELACANTH);
    /** TimePeriod declaration order: the first strict Y match wins, even when the period has no species. */
    private static final Period[] PERIODS = {
            period("quaternary", 2.588F, 0.0F, List.of()),
            period("neogene", 23.03F, 2.589F, List.of()),
            period("paleogene", 66.0F, 23.04F, List.of()),
            period("cretaceous", 145.5F, 66.1F, CRETACEOUS),
            period("jurassic", 201.3F, 145.6F, JURASSIC),
            period("triassic", 252.17F, 201.4F, TRIASSIC),
            period("permian", 298.9F, 252.18F, List.of()),
            period("carboniferous", 358.9F, 299.0F, List.of()),
            period("devonian", 419.2F, 359.0F, DEVONIAN),
            period("silurian", 443.4F, 419.3F, List.of()),
            period("ordovician", 485.4F, 443.5F, List.of()),
            period("cambrian", 541.0F, 485.5F, List.of())
    };

    public FossilDepositFeature() { super(NoneFeatureConfiguration.CODEC); }

    public record Attempt(Species species, int x, int y, int z) {}

    private record Period(String name, float older, float younger, List<Species> species) {
        boolean contains(int y) { return y > yLevel(older) && y < yLevel(younger); }
    }

    /** Same truncation as {@code TimePeriod.getStartYLevel} / {@code getEndYLevel}. */
    static int yLevel(float millionYears) {
        return 64 - (int) (millionYears != 0.0F ? millionYears * 64.0F / 541.0F : 0.0F);
    }

    private static Period period(String name, float older, float younger, List<Species> species) {
        return new Period(name, older, younger, species);
    }

    static List<Species> periodSpecies(String name) {
        for (Period period : PERIODS) if (period.name.equals(name)) return period.species;
        return List.of();
    }

    /** The attempts that place a vein. Empty periods still consume their Y offset, and every hit consumes a vein's random draws. */
    static List<Attempt> plan(RandomSource random, int chunkX, int chunkZ) {
        List<Attempt> attempts = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            Attempt attempt = roll(random, chunkX, chunkZ);
            if (attempt == null) continue;
            attempts.add(attempt);
            drawVein(random, BlockPos.ZERO, (x, y, z) -> {});
        }
        return attempts;
    }

    private static Attempt roll(RandomSource random, int chunkX, int chunkZ) {
        int x = chunkX + random.nextInt(16);
        int y = random.nextInt(64);
        int z = chunkZ + random.nextInt(16);
        Period period = null;
        for (Period candidate : PERIODS) {
            if (candidate.contains(y)) {
                period = candidate;
                break;
            }
        }
        if (period == null) return null;
        y += random.nextInt(8) - 4;
        if (period.species.isEmpty()) return null;
        return new Attempt(period.species.get(random.nextInt(period.species.size())), x, y, z);
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var chunk = context.level().getChunk(context.origin()).getPos();
        RandomSource random = context.random();
        boolean placed = false;
        for (int i = 0; i < ATTEMPTS; i++) {
            Attempt attempt = roll(random, chunk.getMinBlockX(), chunk.getMinBlockZ());
            if (attempt == null) continue;
            BlockState fossil = JurassicContent.FOSSILS.get(attempt.species()).get().defaultBlockState();
            placed |= placeVein(context.level(), random, new BlockPos(attempt.x(), attempt.y(), attempt.z()), fossil);
        }
        return placed;
    }

    /** Places one size-5 stone vein. Random draws match {@code WorldGenMinable} and do not depend on the blocks replaced. */
    static boolean placeVein(WorldGenLevel level, RandomSource random, BlockPos origin, BlockState fossil) {
        boolean[] placed = {false};
        drawVein(random, origin, (x, y, z) -> {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.ensureCanWrite(pos) && level.getBlockState(pos).is(Blocks.STONE))
                placed[0] |= level.setBlock(pos, fossil, 2);
        });
        return placed[0];
    }

    private static void drawVein(RandomSource random, BlockPos origin, VeinBlock consumer) {
        float angle = random.nextFloat() * (float) Math.PI;
        double startX = (float) (origin.getX() + 8) + Mth.sin(angle) * VEIN_SIZE / 8.0F;
        double endX = (float) (origin.getX() + 8) - Mth.sin(angle) * VEIN_SIZE / 8.0F;
        double startZ = (float) (origin.getZ() + 8) + Mth.cos(angle) * VEIN_SIZE / 8.0F;
        double endZ = (float) (origin.getZ() + 8) - Mth.cos(angle) * VEIN_SIZE / 8.0F;
        double startY = origin.getY() + random.nextInt(3) - 2;
        double endY = origin.getY() + random.nextInt(3) - 2;
        for (int i = 0; i < VEIN_SIZE; i++) {
            float along = (float) i / (float) VEIN_SIZE;
            double centerX = startX + (endX - startX) * along;
            double centerY = startY + (endY - startY) * along;
            double centerZ = startZ + (endZ - startZ) * along;
            double radius = random.nextDouble() * VEIN_SIZE / 16.0D;
            double width = (Mth.sin((float) Math.PI * along) + 1.0F) * radius + 1.0D;
            double height = (Mth.sin((float) Math.PI * along) + 1.0F) * radius + 1.0D;
            int minX = Mth.floor(centerX - width / 2.0D);
            int minY = Mth.floor(centerY - height / 2.0D);
            int minZ = Mth.floor(centerZ - width / 2.0D);
            int maxX = Mth.floor(centerX + width / 2.0D);
            int maxY = Mth.floor(centerY + height / 2.0D);
            int maxZ = Mth.floor(centerZ + width / 2.0D);
            for (int x = minX; x <= maxX; x++) {
                double dx = (x + 0.5D - centerX) / (width / 2.0D);
                if (dx * dx >= 1.0D) continue;
                for (int y = minY; y <= maxY; y++) {
                    double dy = (y + 0.5D - centerY) / (height / 2.0D);
                    if (dx * dx + dy * dy >= 1.0D) continue;
                    for (int z = minZ; z <= maxZ; z++) {
                        double dz = (z + 0.5D - centerZ) / (width / 2.0D);
                        if (dx * dx + dy * dy + dz * dz < 1.0D) consumer.accept(x, y, z);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface VeinBlock { void accept(int x, int y, int z); }
}
