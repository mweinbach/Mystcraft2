package ports.jurassicraft.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Retreat from a shared threat when this herd is outmatched or a timid species. */
final class FleeHerdThreatGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private Path escape;
    FleeHerdThreatGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    @Override public boolean canUse() {
        DinosaurHerd herd = dinosaur.herdOrNull();
        if (herd == null || !herd.isFleeing() || dinosaur.isDinosaurSleeping() || dinosaur.disableHerdingTicks() > 0) return false;
        LivingEntity enemy = herd.nearestEnemy(dinosaur);
        if (enemy == null) return false;
        Vec3 away = DefaultRandomPos.getPosAway(dinosaur, 16, 7, enemy.position());
        if (away == null) return false;
        escape = dinosaur.getNavigation().createPath(away.x, away.y, away.z, 0);
        return escape != null;
    }
    @Override public void start() { dinosaur.getNavigation().moveTo(escape, Math.max(1.25, DinosaurHerd.flockSpeed(dinosaur.species()))); }
    @Override public boolean canContinueToUse() {
        DinosaurHerd herd = dinosaur.herdOrNull();
        return herd != null && herd.isFleeing() && dinosaur.disableHerdingTicks() == 0 && !dinosaur.getNavigation().isDone();
    }
    @Override public void stop() { dinosaur.getNavigation().stop(); escape = null; }
}

/** Followers rejoin a wandering leader while retaining room for feeding and breeding goals. */
final class FollowDinosaurHerdGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private DinosaurEntity leader;
    private int elapsed;
    FollowDinosaurHerdGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    @Override public boolean canUse() {
        DinosaurHerd herd = dinosaur.herdOrNull();
        if (herd == null || herd.hasEnemies() || !DinosaurHerd.flocks(dinosaur.species())
                || dinosaur.needsFood() || dinosaur.isDinosaurSleeping() || dinosaur.getTarget() != null
                || dinosaur.disableHerdingTicks() > 0) return false;
        leader = herd.leader();
        return leader != null && leader != dinosaur && leader.isAlive() && dinosaur.distanceToSqr(leader) > 36;
    }
    @Override public void start() { elapsed = 0; dinosaur.getNavigation().moveTo(leader, DinosaurHerd.flockSpeed(dinosaur.species())); }
    @Override public boolean canContinueToUse() {
        DinosaurHerd herd = dinosaur.herdOrNull();
        return leader != null && leader.isAlive() && herd != null && herd.leader() == leader && !herd.hasEnemies()
                && dinosaur.disableHerdingTicks() == 0 && !dinosaur.needsFood() && dinosaur.distanceToSqr(leader) > 16 && elapsed < 300;
    }
    @Override public void tick() {
        if (++elapsed % 20 == 0) dinosaur.getNavigation().moveTo(leader, DinosaurHerd.flockSpeed(dinosaur.species()));
    }
    @Override public void stop() { dinosaur.getNavigation().stop(); leader = null; }
}
