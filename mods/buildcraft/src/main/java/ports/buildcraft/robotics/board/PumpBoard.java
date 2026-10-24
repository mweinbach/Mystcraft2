package ports.buildcraft.robotics.board;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.StationRef;

/**
 * The pump robot, written for this port: removes the fluid source blocks in its work area one bucket at a time, as
 * its owner (protection may refuse, as for breaking the block), into its own tank of
 * {@link RobotEntity#TANK_CAPACITY} mB, paying {@link #PUMP_COST} a bucket as the pump does. It pumps one fluid at a
 * time and empties its tank into the tanks beside its home station once it is full or the area holds no more of
 * that fluid; a home whose tanks take nothing more leaves it resting.
 */
public class PumpBoard extends BlockBoard {
    public static final long PUMP_COST = 10 * Power.MJ;
    private static final int BUCKET = 1000;

    public PumpBoard(RobotEntity robot) {
        super(robot);
    }

    @Override
    protected boolean hasCargo() {
        return !robot.tank().isEmpty();
    }

    @Override
    protected boolean isFull() {
        return robot.tank().space() < BUCKET;
    }

    @Override
    protected boolean isWork(Level level, BlockPos pos) {
        Fluid fluid = BlockUtil.drainableFluid(level, pos);
        return fluid != null && robot.tank().fill(fluid, BUCKET, true) == BUCKET;
    }

    @Override
    protected Progress work(BlockPos pos) {
        ServerLevel level = (ServerLevel) level();
        if (!isWork(level, pos)) return Progress.DONE;
        if (!BlockProtection.mayBreak(level, pos, robot.actor())) return Progress.FAILED;
        if (!robot.spend(PUMP_COST)) return Progress.WORKING;
        Fluid fluid = BlockUtil.drainBlock(level, pos, true);
        if (fluid != null) robot.tank().fill(fluid, BUCKET, false);
        return Progress.DONE;
    }

    /** Flies home and empties the tank into the tanks beside the station, a bucket's worth a tick. */
    @Override
    protected Progress unload() {
        StationRef home = robot.home();
        if (home == null) return Progress.FAILED;
        Progress docking = robot.goDock(home);
        if (docking != Progress.DONE) return docking;
        DockingStationPluggable station = robot.dockedStation();
        Tank tank = robot.tank();
        if (station == null) return Progress.FAILED;
        long filled = station.unloadFluid(robot, tank.fluid(), Math.min(tank.amount(), BUCKET));
        if (filled > 0) tank.drain(null, (int) filled, false);
        if (tank.isEmpty()) return Progress.DONE;
        return filled > 0 ? Progress.WORKING : Progress.FAILED;
    }
}
