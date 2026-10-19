package ports.jurassicraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;

/** Seek a reachable bank and drink from an adjacent water block. */
final class DrinkFromWaterGoal extends MoveToBlockGoal {
    private final DinosaurEntity dinosaur;
    DrinkFromWaterGoal(DinosaurEntity dinosaur) {
        super(dinosaur, 1, 16, 4);
        this.dinosaur = dinosaur;
    }
    @Override public boolean canUse() { return wantsWater() && super.canUse(); }
    @Override public boolean canContinueToUse() {
        return dinosaur.isAlive() && dinosaur.water() < dinosaur.maximumWater() * .9
                && dinosaur.metabolismEnabled() && super.canContinueToUse();
    }
    private boolean wantsWater() {
        return dinosaur.isAlive() && dinosaur.isThirsty() && !dinosaur.isDinosaurSleeping()
                && dinosaur.getTarget() == null && dinosaur.metabolismEnabled();
    }
    @Override protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isSolidRender(level, pos)) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (level.hasChunkAt(neighbor) && (level.getFluidState(neighbor).is(FluidTags.WATER)
                    || level.getFluidState(neighbor.above()).is(FluidTags.WATER))) return true;
        }
        return false;
    }
    @Override public void tick() {
        super.tick();
        if (isReachedTarget() && isValidTarget(dinosaur.level, blockPos)) {
            dinosaur.getLookControl().setLookAt(blockPos.getX() + .5, blockPos.getY() + .5, blockPos.getZ() + .5);
            dinosaur.drink(Math.max(1, dinosaur.maximumWater() / 8));
        }
    }
}
