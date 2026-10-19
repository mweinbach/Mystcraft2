package ports.jurassicraft.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/** Seek covered, dry ground when practical, then sleep in place if no shelter is reachable. */
final class DinosaurSleepGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private Path shelter;
    private int elapsed;

    DinosaurSleepGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (dinosaur.getRandom().nextInt(30) != 0 || !dinosaur.isOnGround()
                || dinosaur.isDinosaurSleeping() || !dinosaur.shouldNaturallySleep()) return false;
        shelter = findShelter();
        return true;
    }

    private Path findShelter() {
        BlockPos origin = dinosaur.blockPosition();
        var level = dinosaur.level;
        if (!level.canSeeSky(origin) && level.getFluidState(origin).isEmpty()) return null;
        for (int i = 0; i < 20; i++) {
            BlockPos column = origin.offset(dinosaur.getRandom().nextInt(17) - 8, 0,
                    dinosaur.getRandom().nextInt(17) - 8);
            if (!level.hasChunkAt(column)) continue;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(pos.getY() - origin.getY()) > 4 || level.canSeeSky(pos)
                    || !level.getFluidState(pos).isEmpty() || level.getFluidState(pos.below()).is(FluidTags.WATER)) continue;
            AABB bounds = dinosaur.getBoundingBox().move(pos.getX() + .5 - dinosaur.getX(),
                    pos.getY() - dinosaur.getY(), pos.getZ() + .5 - dinosaur.getZ());
            if (!level.noCollision(dinosaur, bounds)) continue;
            Path path = dinosaur.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) return path;
        }
        return null;
    }

    @Override public void start() {
        elapsed = 0;
        if (shelter != null) dinosaur.getNavigation().moveTo(shelter, 1);
    }
    @Override public boolean canContinueToUse() {
        return !dinosaur.isDinosaurSleeping() && dinosaur.shouldNaturallySleep() && elapsed <= 400;
    }
    @Override public void tick() {
        if (++elapsed >= 400 || dinosaur.getNavigation().isDone()) dinosaur.setNaturalSleep(true);
    }
    @Override public void stop() { dinosaur.getNavigation().stop(); shelter = null; }
}
