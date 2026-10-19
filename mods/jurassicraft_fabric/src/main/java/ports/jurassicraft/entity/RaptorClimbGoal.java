package ports.jurassicraft.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/** Microraptors approach nearby trunks, climb, and launch away into a glide. */
final class RaptorClimbGoal extends Goal {
    private final DinosaurEntity raptor;
    private BlockPos trunk;
    private Direction side;
    private Path path;
    private double targetX, targetZ, topY;
    private int elapsed;
    private boolean climbing, finished;

    RaptorClimbGoal(DinosaurEntity raptor) {
        this.raptor = raptor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        if (!raptor.isOnGround() || raptor.isDinosaurSleeping() || raptor.getTarget() != null
                || raptor.getRandom().nextInt(300) != 0) return false;
        BlockPos origin = raptor.blockPosition();
        var level = raptor.level;
        for (int i = 0; i < 24; i++) {
            BlockPos column = origin.offset(raptor.getRandom().nextInt(15) - 7, 0,
                    raptor.getRandom().nextInt(15) - 7);
            if (!level.hasChunkAt(column)) continue;
            for (int y = -2; y <= 4; y++) {
                BlockPos base = column.offset(0, y, 0);
                if (!level.getBlockState(base).is(BlockTags.LOGS)) continue;
                int height = 0;
                while (height < 14 && level.getBlockState(base.above(height)).is(BlockTags.LOGS)) height++;
                if (height < 2) continue;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos approach = base.relative(direction);
                    double x = base.getX() + .5 + direction.getStepX() * (.51 + raptor.getBbWidth() / 2);
                    double z = base.getZ() + .5 + direction.getStepZ() * (.51 + raptor.getBbWidth() / 2);
                    AABB bounds = raptor.getBoundingBox().move(x - raptor.getX(), base.getY() - raptor.getY(), z - raptor.getZ());
                    if (!level.noCollision(raptor, bounds)) continue;
                    Path candidate = raptor.getNavigation().createPath(approach, 0);
                    if (candidate != null && candidate.canReach()) {
                        trunk = base; side = direction; path = candidate;
                        targetX = x; targetZ = z; topY = base.getY() + height;
                        return true;
                    }
                }
            }
        }
        return false;
    }
    @Override public void start() {
        elapsed = 0; climbing = false; finished = false;
        raptor.getNavigation().moveTo(path, 1);
    }
    @Override public boolean canContinueToUse() {
        return !finished && elapsed < 400 && !raptor.isDinosaurSleeping() && raptor.getTarget() == null
                && trunk != null && raptor.level.hasChunkAt(trunk);
    }
    @Override public void tick() {
        elapsed++;
        if (!climbing) {
            if (!raptor.getNavigation().isDone()) return;
            if (Math.abs(raptor.getY() - trunk.getY()) > 1.5
                    || raptor.distanceToSqr(targetX, raptor.getY(), targetZ) > 4) { finished = true; return; }
            climbing = true;
            raptor.setClimbingTree(true);
        }
        if (raptor.getY() >= topY - .15) {
            raptor.setYRot(side.toYRot());
            raptor.setDeltaMovement(side.getStepX() * .35, .24, side.getStepZ() * .35);
            finished = true;
            return;
        }
        BlockPos log = new BlockPos(trunk.getX(), Mth.floor(raptor.getY()), trunk.getZ());
        if (!raptor.level.getBlockState(log).is(BlockTags.LOGS)) { finished = true; return; }
        raptor.setYRot(side.getOpposite().toYRot());
        raptor.setDeltaMovement(Mth.clamp(targetX - raptor.getX(), -.12, .12), .3,
                Mth.clamp(targetZ - raptor.getZ(), -.12, .12));
    }
    @Override public void stop() {
        raptor.getNavigation().stop();
        raptor.setClimbingTree(false);
        trunk = null; path = null;
    }
}
