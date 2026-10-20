package ports.buildcraft.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Upstream {@code BlockMarkerPath}: using a path mark reverses its path. */
public class MarkerPathBlock extends MarkerBlock {
    public MarkerPathBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerPathBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player.mayBuild() && level.getBlockEntity(pos) instanceof MarkerPathBlockEntity marker) marker.reverseDirection();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
