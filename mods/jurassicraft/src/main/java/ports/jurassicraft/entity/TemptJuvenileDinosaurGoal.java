package ports.jurassicraft.entity;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/** Juveniles follow a nearby player holding food, with the original species' startle behavior. */
final class TemptJuvenileDinosaurGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private final TargetingConditions targeting;
    private final boolean canScare;
    @Nullable private Player player;
    private int calmDown;
    private double lastX, lastY, lastZ;
    private float lastPitch, lastYaw;

    TemptJuvenileDinosaurGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        canScare = !DinosaurProfile.of(dinosaur.species()).carnivore();
        targeting = TargetingConditions.forNonCombat().range(10).ignoreLineOfSight()
                .selector(entity -> entity instanceof Player candidate && holdsFood(candidate));
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean holdsFood(Player candidate) {
        return dinosaur.isFood(candidate.getMainHandItem()) || dinosaur.isFood(candidate.getOffhandItem());
    }

    private boolean available() {
        return dinosaur.isAlive() && dinosaur.growth() < .5f && !dinosaur.isNoAi()
                && dinosaur.getOrder() == DinosaurEntity.Order.WANDER && !dinosaur.isPassenger()
                && !dinosaur.isNaturallyResting() && !dinosaur.isClimbingTree()
                && !dinosaur.isInjuryAnimating() && !dinosaur.isAttackAnimating()
                && dinosaur.combatAnimation() == DinosaurEntity.CombatAnimation.NONE
                && dinosaur.poseAnimation().isEmpty() && dinosaur.getTarget() == null
                && (dinosaur.herdOrNull() == null || !dinosaur.herdOrNull().hasEnemies());
    }

    boolean consider(@Nullable Player candidate) {
        if (!available() || candidate == null || candidate.level != dinosaur.level
                || !targeting.test(dinosaur, candidate)) return false;
        player = candidate;
        return true;
    }

    @Override public boolean canUse() {
        if (calmDown > 0) { calmDown--; return false; }
        if (!available()) return false;
        return consider(dinosaur.level.getNearestPlayer(targeting, dinosaur));
    }

    @Override public void start() {
        if (player != null) rememberPlayer();
    }

    @Override public boolean canContinueToUse() {
        if (player == null || !available() || player.level != dinosaur.level
                || !targeting.test(dinosaur, player)) return false;
        if (canScare && dinosaur.distanceToSqr(player) < 36
                && (player.distanceToSqr(lastX, lastY, lastZ) > .01
                    || Math.abs(player.getXRot() - lastPitch) > 5 || Math.abs(player.getYRot() - lastYaw) > 5)) return false;
        rememberPlayer();
        return true;
    }

    private void rememberPlayer() {
        lastX = player.getX(); lastY = player.getY(); lastZ = player.getZ();
        lastPitch = player.getXRot(); lastYaw = player.getYRot();
    }

    @Override public void tick() {
        if (player == null) return;
        dinosaur.getLookControl().setLookAt(player, dinosaur.getMaxHeadYRot() + 20, dinosaur.getMaxHeadXRot());
        if (dinosaur.distanceToSqr(player) < 6.25) dinosaur.getNavigation().stop();
        else dinosaur.getNavigation().moveTo(player, .6);
    }

    @Override public void stop() {
        dinosaur.getNavigation().stop();
        player = null;
        calmDown = reducedTickDelay(100);
    }
}
