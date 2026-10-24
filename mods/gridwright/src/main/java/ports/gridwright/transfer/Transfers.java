package ports.gridwright.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.function.Predicate;

/**
 * Moves between two inventories or tanks through the loader APIs: it asks what could move, takes only
 * what the other side would accept, and puts back (or, for items, drops at {@code spill}) anything
 * that unexpectedly does not fit, so nothing is lost or duplicated.
 */
public final class Transfers {

    private Transfers() { }

    private static ItemStack single(ItemStack stack) {
        ItemStack one = stack.copy();
        one.setCount(1);
        return one;
    }

    /**
     * Moves up to {@code max} items of one kind {@code filter} accepts; returns how many moved. Only
     * kinds the destination would take are considered, so a rejected kind never blocks the others.
     */
    public static int items(ServerLevel level, BlockPos from, Direction fromFace, BlockPos to, Direction toFace,
                            Predicate<ItemStack> filter, int max, BlockPos spill) {
        Predicate<ItemStack> wanted = stack -> filter.test(stack)
                && TransferPlatform.insertItems(level, to, toFace, single(stack), true).isEmpty();
        ItemStack probe = TransferPlatform.extractItems(level, from, fromFace, wanted, max, true);
        if (probe.isEmpty()) return 0;
        int accepted = probe.getCount() - TransferPlatform.insertItems(level, to, toFace, probe, true).getCount();
        if (accepted <= 0) return 0;
        ItemStack taken = TransferPlatform.extractItems(level, from, fromFace, stack -> ItemStack.isSameItemSameTags(stack, probe), accepted, false);
        if (taken.isEmpty()) return 0;
        int count = taken.getCount();
        ItemStack stuck = TransferPlatform.insertItems(level, to, toFace, taken, false);
        int moved = count - stuck.getCount();
        if (!stuck.isEmpty()) {
            ItemStack left = TransferPlatform.insertItems(level, from, fromFace, stuck, false);
            if (!left.isEmpty()) Block.popResource(level, spill, left);
        }
        return moved;
    }

    /** Moves up to {@code millibuckets} of one fluid {@code filter} accepts and the destination takes. */
    public static long fluids(ServerLevel level, BlockPos from, Direction fromFace, BlockPos to, Direction toFace,
                              Predicate<Fluid> filter, long millibuckets) {
        Predicate<Fluid> wanted = fluid -> filter.test(fluid) && TransferPlatform.insertFluid(level, to, toFace, fluid, 1, true) > 0;
        FluidAmount probe = TransferPlatform.extractFluid(level, from, fromFace, wanted, millibuckets, true);
        if (probe.isEmpty()) return 0;
        long accepted = TransferPlatform.insertFluid(level, to, toFace, probe.fluid(), probe.millibuckets(), true);
        if (accepted <= 0) return 0;
        FluidAmount taken = TransferPlatform.extractFluid(level, from, fromFace, fluid -> fluid.isSame(probe.fluid()), accepted, false);
        if (taken.isEmpty()) return 0;
        long filled = TransferPlatform.insertFluid(level, to, toFace, taken.fluid(), taken.millibuckets(), false);
        if (filled < taken.millibuckets()) {
            TransferPlatform.insertFluid(level, from, fromFace, taken.fluid(), taken.millibuckets() - filled, false);
        }
        return filled;
    }

    /** Whether some target would take all of {@code stack}. */
    static boolean fits(ServerLevel level, List<PipeNetwork.Target> targets, ItemStack stack) {
        for (PipeNetwork.Target target : targets) {
            if (TransferPlatform.insertItems(level, target.pos(), target.face(), stack, true).isEmpty()) return true;
        }
        return false;
    }

    /** Inserts {@code stack} into the targets in order and returns what none of them took. */
    static ItemStack deliver(ServerLevel level, List<PipeNetwork.Target> targets, ItemStack stack) {
        ItemStack left = stack;
        for (PipeNetwork.Target target : targets) {
            if (left.isEmpty()) break;
            left = TransferPlatform.insertItems(level, target.pos(), target.face(), left, false);
        }
        return left;
    }
}
