package ports.buildcraft.robotics;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.transport.PipeBlockEntity;

/** Where a docking station is: the pipe it is on and the face of that pipe. Written for this port. */
public record StationRef(BlockPos pos, Direction side) {
    public StationRef {
        pos = pos.immutable();
    }

    /** The station, if the pipe is loaded and still carries one on that face. */
    @Nullable
    public DockingStationPluggable resolve(Level level) {
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof PipeBlockEntity pipe && pipe.getPluggable(side) instanceof DockingStationPluggable station
                ? station : null;
    }

    /** The point a docked robot's feet are at: its body sits just outside the station, centred on the face. */
    public Vec3 dockPosition() {
        Vec3 centre = Vec3.atCenterOf(pos).add(side.getStepX() * 0.75, side.getStepY() * 0.75, side.getStepZ() * 0.75);
        return centre.subtract(0, RobotEntity.SIZE / 2, 0);
    }

    public double distanceToSqr(Vec3 point) {
        return dockPosition().distanceToSqr(point);
    }

    public CompoundTag save() {
        CompoundTag tag = NbtUtils.writeBlockPos(pos);
        tag.putByte("side", (byte) side.get3DDataValue());
        return tag;
    }

    public static StationRef load(CompoundTag tag) {
        return new StationRef(NbtUtils.readBlockPos(tag), Direction.from3DDataValue(tag.getByte("side")));
    }
}
