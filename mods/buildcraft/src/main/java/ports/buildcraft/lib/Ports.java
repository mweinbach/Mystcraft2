package ports.buildcraft.lib;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.PortHolder;

/**
 * Neighbour lookups: BuildCraft blocks are reached through their ports directly, everything else
 * through the loader's capability or transfer API.
 */
public final class Ports {
    private Ports() {}

    /** @param side the face of the block at {@code pos} that is being accessed */
    @Nullable
    public static FluidPort fluids(Level level, BlockPos pos, Direction side) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof PortHolder holder) return holder.fluidPort(side);
        return Platform.get().findFluids(level, pos, side);
    }

    @Nullable
    public static ItemPort items(Level level, BlockPos pos, Direction side) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof PortHolder holder) {
            ItemPort port = holder.itemPort(side);
            if (port != null) return port;
        }
        return Platform.get().findItems(level, pos, side);
    }

    @Nullable
    public static EnergyPort energy(Level level, BlockPos pos, Direction side) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof PortHolder holder) return holder.energyPort(side);
        return Platform.get().findEnergy(level, pos, side);
    }

    /** Upstream {@code FluidUtilBC.pushFluidAround}: offers the tank's contents to every neighbour. */
    public static void pushFluidAround(Level level, BlockPos pos, Tank tank) {
        if (tank.isEmpty()) return;
        for (Direction side : Direction.values()) {
            if (tank.isEmpty()) return;
            FluidPort target = fluids(level, pos.relative(side), side.getOpposite());
            if (target == null) continue;
            long used = target.fill(tank.fluid(), tank.amount(), false);
            if (used > 0) tank.drain(null, (int) used, false);
        }
    }
}
