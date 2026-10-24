package ports.buildcraft.robotics.board;

import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.WorkArea;

/**
 * The picker, written for this port: collects dropped items lying in the robot's work area and brings them home,
 * where the station puts them into its pipe or the inventories beside it. With nothing to pick up it rests at home.
 */
public class PickerBoard extends RobotBoard {
    /** Ticks a picker rests when there is nothing to collect. */
    public static final int IDLE_TICKS = 40;
    private static final double REACH = 0.75;

    @Nullable
    private ItemEntity target;
    private boolean unloading;

    public PickerBoard(RobotEntity robot) {
        super(robot);
    }

    @Override
    public void tick() {
        if (unloading) {
            Progress progress = unloadAtHome();
            if (progress == Progress.WORKING) return;
            unloading = false;
            if (progress == Progress.FAILED) robot.sleep(IDLE_TICKS);
            return;
        }
        WorkArea area = robot.workArea();
        if (!isValid(target, area)) {
            drop();
            target = find(area);
            if (target == null) {
                if (robot.isInventoryEmpty()) idleAtHome(IDLE_TICKS);
                else unloading = true;
                return;
            }
            world().claim(robot, target.getUUID());
        }
        robot.flyTowards(target.position());
        if (robot.position().distanceToSqr(target.position()) > REACH * REACH) return;
        ItemStack stack = target.getItem();
        ItemStack left = robot.addToInventory(stack.copy());
        if (left.isEmpty()) target.discard();
        else target.setItem(left);
        drop();
        if (!left.isEmpty() || robot.isInventoryFull()) unloading = true;
    }

    private boolean isValid(@Nullable ItemEntity item, WorkArea area) {
        return item != null && item.isAlive() && area.contains(item.position()) && robot.canHold(item.getItem())
                && !world().isClaimedByOther(robot, item.getUUID());
    }

    @Nullable
    private ItemEntity find(WorkArea area) {
        List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, area.bounds(), item -> isValid(item, area));
        return items.stream().min(Comparator.comparingDouble(item -> item.distanceToSqr(robot))).orElse(null);
    }

    private void drop() {
        if (target != null) world().release(robot, target.getUUID());
        target = null;
    }

    @Override
    public void reset() {
        drop();
        unloading = false;
    }
}
