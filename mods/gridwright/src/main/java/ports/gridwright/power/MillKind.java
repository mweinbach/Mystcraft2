package ports.gridwright.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The phase 2d Grid Power mills and what each needs. The numbers are Gridwright's own: a fire mill
 * gives 8 GP over a fire, a lava mill 4 GP for every side touching a lava source, a manual mill 16 GP
 * while a player keeps cranking it, and a dragon egg mill 64 GP while a dragon egg sits on top.
 */
public enum MillKind {
    FIRE,
    LAVA,
    MANUAL,
    DRAGON_EGG;

    public static final double FIRE_GP = 8.0;
    public static final double LAVA_GP_PER_SIDE = 4.0;
    public static final double MANUAL_GP = 16.0;
    public static final double DRAGON_EGG_GP = 64.0;

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * This mill's output at {@code pos} from its surroundings. The manual mill has no surroundings to
     * read: its output comes from being cranked (see {@link MillBlockEntity#windUp()}).
     */
    public double output(ServerLevel level, BlockPos pos) {
        return switch (this) {
            case FIRE -> isFire(level.getBlockState(pos.below())) ? FIRE_GP : 0;
            case LAVA -> lava(lavaSides(level, pos));
            case MANUAL -> 0;
            case DRAGON_EGG -> level.getBlockState(pos.above()).is(Blocks.DRAGON_EGG) ? DRAGON_EGG_GP : 0;
        };
    }

    /** Fire, soul fire or a lit campfire. */
    public static boolean isFire(BlockState state) {
        return state.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state);
    }

    public static double lava(int sides) {
        return LAVA_GP_PER_SIDE * Math.max(0, Math.min(4, sides));
    }

    /** Horizontal neighbours holding a lava source (flowing lava does not count). */
    public static int lavaSides(ServerLevel level, BlockPos pos) {
        int sides = 0;
        for (Direction direction : HORIZONTAL) {
            FluidState fluid = level.getFluidState(pos.relative(direction));
            if (fluid.is(FluidTags.LAVA) && fluid.isSource()) sides++;
        }
        return sides;
    }
}
