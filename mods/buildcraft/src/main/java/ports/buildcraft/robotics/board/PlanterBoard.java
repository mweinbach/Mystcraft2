package ports.buildcraft.robotics.board;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;

/**
 * The planter, written for this port: takes plantables from beside its home station and plants them in its work
 * area as its owner (protection may refuse, as for a block placed there), one item a plant. Crop seeds (crops and
 * melon and pumpkin stems) go on farmland, saplings on dirt or grass, nether wart where it can grow, each only where
 * the plant could survive and has air to grow into. With nowhere left for what it carries, it takes the rest home and
 * fetches another kind; with nothing left to plant anywhere it rests.
 */
public class PlanterBoard extends BlockBoard {
    public PlanterBoard(RobotEntity robot) {
        super(robot);
    }

    /** The plant {@code stack} places, if the planter plants it. */
    @Nullable
    public static Block plant(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        Block block = item.getBlock();
        boolean plantable = block instanceof CropBlock || block instanceof StemBlock || block instanceof SaplingBlock || block instanceof NetherWartBlock;
        return plantable ? block : null;
    }

    public static boolean isPlantable(ItemStack stack) {
        return plant(stack) != null;
    }

    /** Whether {@code plant} may be planted at {@code pos}, which must be empty, on the right soil and where it survives. */
    public static boolean canPlant(Level level, Block plant, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) return false;
        BlockState soil = level.getBlockState(pos.below());
        if (plant instanceof CropBlock || plant instanceof StemBlock) {
            if (!(soil.getBlock() instanceof FarmBlock)) return false;
        } else if (plant instanceof SaplingBlock) {
            if (!soil.is(BlockTags.DIRT)) return false;
        }
        return plant.defaultBlockState().canSurvive(level, pos);
    }

    @Override
    protected boolean prepare() {
        return haveItems(PlanterBoard::isPlantable);
    }

    @Override
    protected boolean isWork(Level level, BlockPos pos) {
        return slotFor(level, pos) >= 0;
    }

    /** The slot of a carried plantable that may be planted at {@code pos}, or -1. */
    private int slotFor(Level level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) return -1;
        return slotOf(stack -> {
            Block plant = plant(stack);
            return plant != null && canPlant(level, plant, pos);
        });
    }

    @Override
    protected Progress work(BlockPos pos) {
        ServerLevel level = (ServerLevel) level();
        int slot = slotFor(level, pos);
        if (slot < 0) return Progress.DONE;
        Block plant = plant(robot.inventory().getItem(slot));
        if (!BlockProtection.mayPlace(level, pos, Direction.UP, level.getBlockState(pos.below()), robot.actor())) return Progress.FAILED;
        if (!robot.spend(USE_COST)) return Progress.WORKING;
        BlockState state = plant.defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        robot.inventory().removeItem(slot, 1);
        return Progress.DONE;
    }
}
