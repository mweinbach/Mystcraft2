package ports.jurassicraft.tourrail;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.vehicle.ExplorerEntity;
import ports.jurassicraft.vehicle.VehicleEntity;

/** Server-side center-to-center path following, preserving position and travel direction. */
public final class TourRailTravel {
    private final ExplorerEntity explorer;
    private BlockPos current, next, previous;
    private double progress;
    private boolean reversed;
    public TourRailTravel(ExplorerEntity explorer) { this.explorer = explorer; }
    private Level level() { return explorer.level; }
    public boolean tick(int controls) {
        if (current == null) {
            BlockPos found = findUnderVehicle();
            if (found == null) return false;
            current = found;
            progress = 0;
            previous = null;
            explorer.setOnTourRail(true);
            Vec3 center = center(found);
            explorer.setPos(center.x, center.y, center.z);
            next = pickNext(found, null, explorer.getYRot() + (reversed ? 180 : 0));
        }
        if (!level().hasChunkAt(current)) { explorer.setDeltaMovement(Vec3.ZERO); return true; }
        if (!TourRailConnections.isRail(level(), current)) { leave(); return false; }
        if (next != null && !level().hasChunkAt(next)) { explorer.setDeltaMovement(Vec3.ZERO); return true; }
        if (next != null && !TourRailConnections.connected(level(), current, next)) {
            next = null; progress = 0;
            Vec3 center = center(current);
            explorer.setPos(center.x, center.y, center.z);
        }
        boolean back = (controls & VehicleEntity.BACK) != 0, forward = (controls & VehicleEntity.FORWARD) != 0;
        if (back != forward && back != reversed) {
            reversed = back;
            if (next != null) {
                BlockPos old = current; current = next; next = old;
                previous = null; progress = 1 - progress;
            } else if (previous != null && TourRailConnections.connected(level(), current, previous)) {
                next = previous; previous = null; progress = 0;
            } else next = pickNext(current, null, explorer.getYRot() + (reversed ? 180 : 0));
        }
        if (next == null) next = pickNext(current, previous, explorer.getYRot() + (reversed ? 180 : 0));
        if (explorer.getPassengers().isEmpty() || next == null) {
            explorer.setDeltaMovement(Vec3.ZERO);
            return true;
        }
        Vec3 start = center(current), end = center(next);
        double distance = start.distanceTo(end);
        double speed = ((TourRailBlock)level().getBlockState(current).getBlock()).speed();
        double nextProgress = Math.min(1, progress + speed / distance);
        Vec3 line = start.lerp(end, nextProgress);
        Vec3 desired = new Vec3(line.x, TourRailPath.height(start.y, end.y, nextProgress), line.z);
        explorer.move(MoverType.SELF, desired.subtract(explorer.position()));
        explorer.setDeltaMovement(Vec3.ZERO);
        if (explorer.position().distanceToSqr(desired) > .01) return true; // obstruction: wait until clear
        progress = nextProgress;
        float yaw = (float)Math.toDegrees(-Math.atan2(end.x - start.x, end.z - start.z));
        explorer.setYRot(Mth.wrapDegrees(yaw + (reversed ? 180 : 0)));
        if (progress >= 1) {
            BlockPos arrivedFrom = current;
            current = next; previous = arrivedFrom; progress = 0;
            next = pickNext(current, previous, explorer.getYRot() + (reversed ? 180 : 0));
        }
        return true;
    }
    @Nullable private BlockPos findUnderVehicle() {
        BlockPos pos = explorer.blockPosition();
        for (int offset = 0; offset <= 2; offset++) {
            BlockPos candidate = pos.below(offset);
            if (!TourRailConnections.isRail(level(), candidate)) continue;
            if (offset == 2) {
                // The extra reach is only for sloped rails. Do not teleport a car
                // through a solid floor onto a track underneath a bridge.
                if (!level().getBlockState(candidate).getValue(TourRailBlock.SHAPE).ascending()
                        || !level().getBlockState(pos.below()).getCollisionShape(level(), pos.below()).isEmpty()) continue;
            }
            return candidate;
        }
        return null;
    }
    @Nullable private BlockPos pickNext(BlockPos rail, @Nullable BlockPos cameFrom, float yaw) {
        TourRailShape shape = level().getBlockState(rail).getValue(TourRailBlock.SHAPE);
        BlockPos a = TourRailConnections.neighbor(level(), rail, shape.first());
        BlockPos b = TourRailConnections.neighbor(level(), rail, shape.second());
        if (cameFrom != null) {
            if (cameFrom.equals(a)) a = null;
            if (cameFrom.equals(b)) b = null;
        }
        if (a == null) return b;
        if (b == null) return a;
        double x = -Math.sin(Math.toRadians(yaw)), z = Math.cos(Math.toRadians(yaw));
        double dotA = (a.getX() - rail.getX()) * x + (a.getZ() - rail.getZ()) * z;
        double dotB = (b.getX() - rail.getX()) * x + (b.getZ() - rail.getZ()) * z;
        return dotA >= dotB ? a : b;
    }
    private static Vec3 center(BlockPos pos) { return new Vec3(pos.getX() + .5, pos.getY() + .125, pos.getZ() + .5); }
    private void leave() {
        current = next = previous = null;
        progress = 0;
        explorer.setOnTourRail(false);
    }
    public void save(CompoundTag tag) {
        if (current == null) return;
        CompoundTag rail = new CompoundTag();
        rail.putLong("Current", current.asLong());
        if (next != null) rail.putLong("Next", next.asLong());
        if (previous != null) rail.putLong("Previous", previous.asLong());
        rail.putDouble("Progress", progress);
        rail.putBoolean("Reversed", reversed);
        tag.put("TourRail", rail);
    }
    public void load(CompoundTag tag) {
        if (!tag.contains("TourRail", 10)) { leave(); return; }
        CompoundTag rail = tag.getCompound("TourRail");
        if (!rail.contains("Current", 4)) { leave(); return; }
        current = BlockPos.of(rail.getLong("Current"));
        next = rail.contains("Next", 4) ? BlockPos.of(rail.getLong("Next")) : null;
        previous = rail.contains("Previous", 4) ? BlockPos.of(rail.getLong("Previous")) : null;
        double raw = rail.getDouble("Progress");
        progress = Double.isFinite(raw) ? Mth.clamp(raw, 0, 1) : 0;
        reversed = rail.getBoolean("Reversed");
        explorer.setOnTourRail(true);
    }
}
