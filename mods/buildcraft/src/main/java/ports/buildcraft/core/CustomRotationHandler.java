package ports.buildcraft.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream {@code ICustomRotationHandler}: blocks the wrench turns from its own use, which runs when
 * the block does not handle the click itself (for example while sneaking).
 */
public interface CustomRotationHandler {
    /** {@link InteractionResult#PASS} if nothing happened, {@link InteractionResult#FAIL} if no rotation fits. */
    InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched);
}
