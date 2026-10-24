package ports.buildcraft.builders.snapshot;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream {@code SchematicBlockContext}: the block being scanned and the scanned box's minimum corner. A
 * {@linkplain #detached detached} context has no level: it describes a block read from saved data, whose block
 * entity data comes with it, so a schematic can be rebuilt from the rules alone.
 */
public record SchematicBlockContext(@Nullable Level level, BlockPos basePos, BlockPos pos, BlockState blockState, Block block,
        @Nullable CompoundTag storedTileNbt) {
    public SchematicBlockContext(Level level, BlockPos basePos, BlockPos pos) {
        this(level, basePos, pos, level.getBlockState(pos), level.getBlockState(pos).getBlock(), null);
    }

    public static SchematicBlockContext detached(BlockState blockState, @Nullable CompoundTag tileNbt) {
        return new SchematicBlockContext(null, BlockPos.ZERO, BlockPos.ZERO, blockState, blockState.getBlock(), tileNbt);
    }
}
