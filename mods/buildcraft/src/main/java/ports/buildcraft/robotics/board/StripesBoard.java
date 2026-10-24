package ports.buildcraft.robotics.board;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.transport.PipeItem;
import ports.buildcraft.transport.stripes.StripesActivator;
import ports.buildcraft.transport.stripes.StripesRegistry;

/**
 * The stripes robot, written for this port: takes items from beside its home station and uses them in its work area
 * the way a stripes pipe pointing down uses the items that reach its end, through the same handlers
 * ({@link StripesRegistry}) as its owner's fake player: seeds and saplings are planted, hoes till, shears shear, items
 * with a dispenser behaviour are dispensed, blocks are placed and so on. It works on free spots: air above a block
 * with a collision box that is not a block entity (so it never builds on machines, chests or pipes). What the
 * handlers give back (sheared drops, an emptied bucket) goes into its inventory and home. Pipes are left at home,
 * since laying pipe needs a stripes pipe. A spot where an item does nothing is skipped; items that found no use in a
 * whole sweep are left at home while it has others to try.
 */
public class StripesBoard extends BlockBoard {
    private final StripesActivator activator = (stack, from) -> {
        ItemStack left = robot.addToInventory(stack);
        if (!left.isEmpty()) robot.spawnAtLocation(left);
        return true;
    };

    public StripesBoard(RobotEntity robot) {
        super(robot);
    }

    private boolean isUsable(ItemStack stack) {
        return !(stack.getItem() instanceof PipeItem);
    }

    /** Air above a block with a collision box that is no block entity. */
    public static boolean isFreeSpot(Level level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) return false;
        BlockPos below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockEntity(below) == null;
    }

    @Override
    protected boolean prepare() {
        return haveItems(this::isUsable);
    }

    @Override
    protected boolean isWork(Level level, BlockPos pos) {
        return isFreeSpot(level, pos);
    }

    /**
     * Asked as using the first usable stack on the spot from above; the stripes handlers ask protection again for
     * what they actually do.
     */
    @Override
    protected boolean mayWork(ServerLevel level, BlockPos pos) {
        int slot = slotOf(this::isUsable);
        if (slot < 0) return true;
        ServerPlayer player = robot.actor();
        ItemStack stack = robot.inventory().getItem(slot);
        return BlockProtection.mayUse(level, pos, Direction.UP, stack, player);
    }

    /**
     * Uses the first usable stack on {@code pos} from just above it. The handlers may use up some of it or change it
     * (a hoe wears); if everything comes back unchanged the item did nothing there and the spot is skipped.
     */
    @Override
    protected Progress work(BlockPos pos) {
        int slot = slotOf(this::isUsable);
        if (slot < 0 || !isFreeSpot(level(), pos)) return Progress.DONE;
        if (!robot.spend(USE_COST)) return Progress.WORKING;
        ItemStack stack = robot.inventory().removeItemNoUpdate(slot);
        ServerPlayer player = robot.actor();
        Inventory hands = player.getInventory();
        hands.clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        boolean used = StripesRegistry.handleItem((ServerLevel) level(), pos.above(), Direction.DOWN, player.getMainHandItem(), player, activator);
        List<ItemStack> back = new ArrayList<>();
        for (int index = 0; index < hands.getContainerSize(); index++) {
            ItemStack left = hands.removeItemNoUpdate(index);
            if (!left.isEmpty()) back.add(left);
        }
        boolean unchanged = back.size() == 1 && ItemStack.matches(back.get(0), stack);
        for (ItemStack left : back) activator.sendItem(left, Direction.DOWN);
        return used && !unchanged ? Progress.DONE : Progress.FAILED;
    }
}
