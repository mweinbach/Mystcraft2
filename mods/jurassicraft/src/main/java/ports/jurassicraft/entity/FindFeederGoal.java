package ports.jurassicraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import ports.jurassicraft.feeder.FeederBlockEntity;

/** Hungry land animals seek a nearby loaded feeder containing food for their diet. */
public final class FindFeederGoal extends MoveToBlockGoal {
    private final DinosaurEntity dinosaur;
    public FindFeederGoal(DinosaurEntity dinosaur) {
        super(dinosaur, 1.0, 12, 4);
        this.dinosaur = dinosaur;
    }
    @Override public boolean canUse() { return dinosaur.needsFood() && super.canUse(); }
    @Override public boolean canContinueToUse() { return dinosaur.needsFood() && super.canContinueToUse(); }
    @Override protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FeederBlockEntity feeder && feeder.hasFoodFor(dinosaur);
    }
    @Override public double acceptedDistance() { return 1.5; }
}
