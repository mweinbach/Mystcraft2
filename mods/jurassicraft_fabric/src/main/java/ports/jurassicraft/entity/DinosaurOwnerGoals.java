package ports.jurassicraft.entity;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

/** Follow the imprinted player without teleporting or interrupting combat. */
final class FollowDinosaurOwnerGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private Player owner;
    private int recalculate;
    private float oldWaterCost;

    FollowDinosaurOwnerGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    boolean canFollow(@Nullable Player candidate) {
        return candidate != null && candidate.isAlive() && candidate.level == dinosaur.level
                && candidate.getUUID().equals(dinosaur.getOwnerUUID()) && dinosaur.getOrder() == DinosaurEntity.Order.FOLLOW
                && dinosaur.isAlive() && !dinosaur.isDinosaurSleeping() && dinosaur.getTarget() == null
                && dinosaur.getNavigation().isDone() && dinosaur.distanceToSqr(candidate) > dinosaur.getBbWidth() * 40;
    }

    @Override public boolean canUse() {
        Player candidate = dinosaur.getOwnerUUID() == null ? null : dinosaur.level.getPlayerByUUID(dinosaur.getOwnerUUID());
        if (!canFollow(candidate)) return false;
        owner = candidate;
        return true;
    }

    @Override public void start() {
        oldWaterCost = dinosaur.getPathfindingMalus(BlockPathTypes.WATER);
        dinosaur.setPathfindingMalus(BlockPathTypes.WATER, 0);
        recalculate = 0;
        dinosaur.getNavigation().moveTo(owner, .8);
    }

    @Override public boolean canContinueToUse() {
        return owner != null && owner.isAlive() && owner.level == dinosaur.level
                && owner.getUUID().equals(dinosaur.getOwnerUUID()) && dinosaur.getOrder() == DinosaurEntity.Order.FOLLOW
                && dinosaur.isAlive() && !dinosaur.isDinosaurSleeping() && dinosaur.getTarget() == null
                && !dinosaur.getNavigation().isDone() && !dinosaur.getBoundingBox().inflate(2).intersects(owner.getBoundingBox());
    }

    @Override public void tick() {
        dinosaur.getLookControl().setLookAt(owner, 10, dinosaur.getMaxHeadXRot());
        if (--recalculate <= 0) {
            recalculate = 10;
            if (dinosaur.distanceToSqr(owner) > dinosaur.getBbWidth() * 40) dinosaur.getNavigation().moveTo(owner, .8);
        }
    }

    @Override public void stop() {
        dinosaur.getNavigation().stop();
        dinosaur.setPathfindingMalus(BlockPathTypes.WATER, oldWaterCost);
        owner = null;
    }
}

/** Adults of defending species help their owner only while following. */
final class DefendDinosaurOwnerGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private final boolean respondToAttacker;
    private LivingEntity target;

    DefendDinosaurOwnerGoal(DinosaurEntity dinosaur, boolean respondToAttacker) {
        this.dinosaur = dinosaur;
        this.respondToAttacker = respondToAttacker;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    boolean consider(@Nullable Player owner) {
        if (owner == null || !owner.isAlive() || owner.level != dinosaur.level
                || !owner.getUUID().equals(dinosaur.getOwnerUUID()) || !dinosaur.defendsOwner()
                || dinosaur.getOrder() != DinosaurEntity.Order.FOLLOW || dinosaur.growth() <= .5f
                || !dinosaur.isAlive() || dinosaur.isDinosaurSleeping()
                || dinosaur.herdOrNull() != null && dinosaur.herdOrNull().isFleeing()) return false;
        LivingEntity candidate = respondToAttacker ? owner.getLastHurtByMob() : owner.getLastHurtMob();
        if (candidate == null || candidate == dinosaur || !candidate.isAlive() || candidate.isRemoved()
                || candidate.level != dinosaur.level || !dinosaur.canAttack(candidate)) return false;
        target = candidate;
        return true;
    }

    @Override public boolean canUse() {
        Player owner = dinosaur.getOwnerUUID() == null ? null : dinosaur.level.getPlayerByUUID(dinosaur.getOwnerUUID());
        return consider(owner);
    }

    @Override public void start() { dinosaur.setTarget(target); }

    @Override public boolean canContinueToUse() {
        return target != null && target.isAlive() && !target.isRemoved() && dinosaur.getTarget() == target
                && dinosaur.isAlive() && !dinosaur.isDinosaurSleeping() && dinosaur.getOrder() == DinosaurEntity.Order.FOLLOW;
    }

    @Override public void stop() {
        if (dinosaur.getTarget() == target) dinosaur.setTarget(null);
        target = null;
    }
}
