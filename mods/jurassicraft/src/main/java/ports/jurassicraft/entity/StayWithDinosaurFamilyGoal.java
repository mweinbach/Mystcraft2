package ports.jurassicraft.entity;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Return toward the nest and the center of the parents when the family drifts apart. */
final class StayWithDinosaurFamilyGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private double x, y, z;
    StayWithDinosaurFamilyGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    @Override public boolean canUse() {
        if (!(dinosaur.level instanceof ServerLevel level) || dinosaur.isOrderedToSit() || dinosaur.isDinosaurSleeping()
                || dinosaur.getTarget() != null || dinosaur.getRandom().nextInt(20) != 0) return false;
        DinosaurFamilyData.Family family = DinosaurFamilyData.get(level).family(dinosaur.getUUID());
        if (family == null || family.home == null) return false;
        BlockPos home = family.home;
        x = home.getX() + .5; y = home.getY(); z = home.getZ() + .5;
        if (level.getGameTime() >= family.stayHomeUntil) {
            double sumX = 0, sumZ = 0; int count = 0;
            for (UUID id : family.parents) {
                Entity parent = level.getEntity(id);
                if (parent instanceof DinosaurEntity d && d.isAlive() && !d.isCarcass()) {
                    sumX += d.getX(); sumZ += d.getZ(); count++;
                }
            }
            if (count > 0) { x = (x + sumX / count) / 2; z = (z + sumZ / count) / 2; }
        }
        double dx = dinosaur.getX() - x, dz = dinosaur.getZ() - z;
        return dx * dx + dz * dz > 128;
    }
    @Override public void start() { dinosaur.getNavigation().moveTo(x, y, z, .8); }
    @Override public boolean canContinueToUse() {
        return !dinosaur.getNavigation().isDone() && dinosaur.getTarget() == null && !dinosaur.isOrderedToSit();
    }
}
