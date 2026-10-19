package ports.jurassicraft.plants;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import ports.jurassicraft.PortMod;

/** The original Overworld generator buried a randomly oriented ancient trunk. */
final class PetrifiedTreeFeature extends Feature<NoneFeatureConfiguration> {
    private static final List<PlantSpecies> TREES = List.of(PlantSpecies.GINKGO, PlantSpecies.CALAMITES,
            PlantSpecies.PSARONIUS, PlantSpecies.PHOENIX, PlantSpecies.ARAUCARIA);
    private static final TagKey<Block> REPLACEABLE = TagKey.create(net.minecraft.core.Registry.BLOCK_REGISTRY, new ResourceLocation(PortMod.MOD_ID, "petrified_tree_replaceable"));

    PetrifiedTreeFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos surface = context.origin();
        int bottom = level.getMinBuildHeight();
        int top = surface.getY() - 10;
        if (top <= bottom) return false;
        BlockPos start = new BlockPos(surface.getX(), bottom + random.nextInt(top - bottom), surface.getZ());
        PlantSpecies species = TREES.get(random.nextInt(TREES.size()));
        double pitch = random.nextDouble() * Math.PI * 2.0;
        double yaw = random.nextDouble() * Math.PI * 2.0;
        double horizontal = Math.cos(pitch);
        return generateTrunk(level, start, species, 3 + random.nextInt(7),
                -Math.sin(yaw) * horizontal, Math.sin(pitch), Math.cos(yaw) * horizontal);
    }

    static boolean generateTrunk(WorldGenLevel level, BlockPos start, PlantSpecies species, int length,
            double dx, double dy, double dz) {
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        Direction.Axis axis = ay >= ax && ay >= az ? Direction.Axis.Y : ax >= az ? Direction.Axis.X : Direction.Axis.Z;
        BlockState log = PlantRegistry.petrifiedLog(species).get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        boolean placed = false;
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.offset((int) Math.round(dx * i), (int) Math.round(dy * i), (int) Math.round(dz * i));
            if (level.isOutsideBuildHeight(pos) || !level.ensureCanWrite(pos)) continue;
            BlockState previous = level.getBlockState(pos);
            if (!previous.is(REPLACEABLE) || previous.hasBlockEntity() || !previous.getFluidState().isEmpty()) continue;
            placed |= level.setBlock(pos, log, Block.UPDATE_CLIENTS);
        }
        return placed;
    }
}
