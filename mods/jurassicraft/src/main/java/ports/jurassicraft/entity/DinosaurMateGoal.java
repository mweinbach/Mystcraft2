package ports.jurassicraft.entity;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** Find a healthy adult mate and approach before beginning gestation. */
final class DinosaurMateGoal extends Goal {
    private final DinosaurEntity mother;
    private DinosaurEntity mate;
    private int approaching;
    DinosaurMateGoal(DinosaurEntity mother) {
        this.mother = mother;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        if (!(mother.level instanceof ServerLevel server) || mother.isMale() || !mother.readyToBreed()
                || mother.getRandom().nextInt(200) != 0) return false;
        mate = DinosaurFamilyData.get(server).mate(mother);
        approaching = 0;
        return mate != null && mother.distanceToSqr(mate) < 128 && mother.canBreedWith(mate);
    }
    @Override public boolean canContinueToUse() {
        return mate != null && mother.canBreedWith(mate) && approaching < 300;
    }
    @Override public void tick() {
        approaching++;
        mother.getLookControl().setLookAt(mate, 10, 10);
        if (approaching % 10 == 1) mother.getNavigation().moveTo(mate, 1.0);
        if (mother.distanceToSqr(mate) <= Math.max(9, mother.getBbWidth() * mother.getBbWidth())) {
            mother.beginBreeding(mate);
        }
    }
    @Override public void stop() { mate = null; mother.getNavigation().stop(); }
}
