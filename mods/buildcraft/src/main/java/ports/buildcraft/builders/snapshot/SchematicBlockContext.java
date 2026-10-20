package ports.buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Upstream {@code SchematicBlockContext}: the block being scanned and the scanned box's minimum corner. */
public record SchematicBlockContext(Level level, BlockPos basePos, BlockPos pos, BlockState blockState, Block block) {
    public SchematicBlockContext(Level level, BlockPos basePos, BlockPos pos) {
        this(level, basePos, pos, level.getBlockState(pos), level.getBlockState(pos).getBlock());
    }
}
