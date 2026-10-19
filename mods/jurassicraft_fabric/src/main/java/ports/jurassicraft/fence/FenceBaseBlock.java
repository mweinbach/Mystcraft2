package ports.jurassicraft.fence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Relay redstone changes to the light on the pole above this base. */
public final class FenceBaseBlock extends Block {
    public FenceBaseBlock(Properties properties) { super(properties); }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos from, boolean moving) {
        if (!level.isClientSide) FencePoleBlock.refresh(level, pos.above());
    }
}
