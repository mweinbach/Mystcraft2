package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import ports.jurassicraft.platform.Platform;

/** Uses the original two sapling stages and each species' historic tree layout. */
public final class AncientSaplingBlock extends SaplingBlock {
    private final PlantSpecies species;

    public AncientSaplingBlock(PlantSpecies species) {
        super(null, BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING));
        this.species = species;
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_CLIENTS);
        } else if (Platform.allowTreeGrowth(level, random, pos)) {
            grow(level, pos, random);
        }
    }

    void grow(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!PlantChunks.isAreaLoaded(level, pos, 10) || !level.getBlockState(pos).is(this)) return;
        AncientTreeShape.Layout shape = AncientTreeShape.create(species, random,
                PlantRegistry.log(species).get().defaultBlockState(), PlantRegistry.leaves(species).get().defaultBlockState());
        // A blocked trunk or height limit leaves the sapling intact. Branches and
        // leaves may still grow around obstructions without replacing them.
        for (int y = 1; y < shape.height(); y++) {
            BlockPos target = pos.above(y);
            if (level.isOutsideBuildHeight(target) || !replaceable(level.getBlockState(target))) return;
        }
        for (BlockPos offset : shape.blocks().keySet())
            if (level.isOutsideBuildHeight(pos.offset(offset))) return;
        shape.blocks().forEach((offset, placement) -> {
            BlockPos target = pos.offset(offset);
            BlockState existing = level.getBlockState(target);
            if (target.equals(pos) || (placement.airOnly() ? existing.isAir() : replaceable(existing)))
                level.setBlock(target, placement.state(), Block.UPDATE_ALL);
        });
    }

    static boolean replaceable(BlockState state) {
        return !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && (state.isAir() || state.is(BlockTags.LEAVES) || state.getMaterial().isReplaceable());
    }
}
