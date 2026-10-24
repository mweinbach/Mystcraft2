package ports.buildcraft.robotics.board;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.RobotWorld;
import ports.buildcraft.robotics.StationRef;

/**
 * A robot's job, written for this port. The robot calls {@link #tick()} every tick it is awake, charged and not busy
 * recharging; a board keeps whatever it needs between ticks in fields and starts over from {@link #reset()} (boards
 * hold no saved state: a reloaded robot simply looks for work again).
 */
public abstract class RobotBoard {
    protected final RobotEntity robot;

    protected RobotBoard(RobotEntity robot) {
        this.robot = robot;
    }

    public abstract void tick();

    /** Forgets the current job, for example when the robot leaves to recharge. */
    public void reset() {}

    protected Level level() {
        return robot.level;
    }

    protected RobotWorld world() {
        return RobotWorld.get(robot.level);
    }

    /**
     * Brings the robot home and hands over what it carries; {@link Progress#DONE} once empty, {@link Progress#FAILED}
     * when it has no home or home will not take everything (the robot then waits a while before trying again).
     */
    protected Progress unloadAtHome() {
        StationRef home = robot.home();
        if (home == null) return Progress.FAILED;
        Progress docking = robot.goDock(home);
        if (docking != Progress.DONE) return docking;
        int before = count();
        Progress unload = robot.unloadOne();
        if (unload == Progress.WORKING) return Progress.WORKING;
        if (robot.isInventoryEmpty()) return Progress.DONE;
        return count() < before ? Progress.WORKING : Progress.FAILED;
    }

    private int count() {
        int count = 0;
        for (int slot = 0; slot < robot.inventory().getContainerSize(); slot++) {
            count += robot.inventory().getItem(slot).getCount();
        }
        return count;
    }

    /** Flies home and rests there for {@code ticks}; true once it rests (at home, or without a home to reach). */
    protected boolean idleAtHome(int ticks) {
        StationRef home = robot.home();
        if (home != null && robot.goDock(home) == Progress.WORKING) return false;
        robot.sleep(ticks);
        return true;
    }

    /**
     * True once the robot holds a tool that passes {@code tools}. Otherwise it puts away a tool that does not suit
     * this board, then flies home and takes a suitable one from the inventories beside its station. With none there it
     * takes home what it carries ({@link #cargoToUnload()}) or, carrying nothing, rests.
     */
    protected boolean haveTool(Predicate<ItemStack> tools) {
        if (robot.hasTool(tools)) return true;
        if (!robot.tool().isEmpty()) {
            ItemStack left = robot.addToInventory(robot.tool());
            robot.setTool(left);
            if (!left.isEmpty()) cargoToUnload();
            return false;
        }
        StationRef home = robot.home();
        if (home == null) {
            robot.sleep(TOOL_IDLE_TICKS);
            return false;
        }
        Progress docking = robot.goDock(home);
        if (docking == Progress.WORKING) return false;
        DockingStationPluggable station = robot.dockedStation();
        ItemStack tool = docking == Progress.DONE && station != null ? station.extract(tools, 1) : ItemStack.EMPTY;
        if (!tool.isEmpty()) {
            robot.setTool(tool);
        } else if (!robot.isInventoryEmpty()) {
            cargoToUnload();
        } else {
            robot.sleep(TOOL_IDLE_TICKS);
        }
        return false;
    }

    /** How long a robot without a tool to fetch rests before it looks again. */
    public static final int TOOL_IDLE_TICKS = 100;

    /** Asks the board to bring the robot's cargo home; boards that never carry anything just let the robot rest. */
    protected void cargoToUnload() {
        robot.sleep(TOOL_IDLE_TICKS);
    }

    @Nullable
    protected DockingStationPluggable homeStation() {
        StationRef home = robot.home();
        return home == null ? null : home.resolve(robot.level);
    }
}
