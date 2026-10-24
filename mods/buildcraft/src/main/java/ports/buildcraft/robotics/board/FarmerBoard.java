package ports.buildcraft.robotics.board;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;

/**
 * The farmer, written for this port: takes a hoe from beside its home station and tills the dirt, grass and paths
 * with air above them in its work area into farmland, as its owner (protection may refuse, as for a block placed
 * there). Each block costs {@link #USE_COST} and wears the hoe by one.
 */
public class FarmerBoard extends BlockBoard {
    public FarmerBoard(RobotEntity robot) {
        super(robot);
    }

    public static boolean isHoe(ItemStack stack) {
        return stack.getItem() instanceof HoeItem;
    }

    public static boolean isTillable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH);
    }

    @Override
    protected boolean prepare() {
        return haveTool(FarmerBoard::isHoe);
    }

    @Override
    protected boolean isWork(Level level, BlockPos pos) {
        return isTillable(level.getBlockState(pos)) && level.isEmptyBlock(pos.above());
    }

    @Override
    protected Progress work(BlockPos pos) {
        ServerLevel level = (ServerLevel) level();
        BlockState state = level.getBlockState(pos);
        if (!isWork(level, pos)) return Progress.DONE;
        if (!BlockProtection.mayPlace(level, pos, Direction.UP, state, robot.actor())) return Progress.FAILED;
        if (!robot.spend(USE_COST)) return Progress.WORKING;
        level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        robot.damageTool(1);
        return Progress.DONE;
    }
}
