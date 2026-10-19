package ports.jurassicraft.entity;

import java.util.EnumSet;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;

/** Spit until the victim is both blinded and wounded, then switch to biting. */
final class DilophosaurusSpitGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private LivingEntity target;
    private int cooldown;
    private int windup;
    private int visibleTicks;
    private int animationTicks;

    DilophosaurusSpitGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    static boolean shouldBite(LivingEntity target) {
        return target != null && target.isAlive() && target.getHealth() < target.getMaxHealth() * .9f
                && target.hasEffect(MobEffects.BLINDNESS);
    }

    @Override public boolean canUse() {
        LivingEntity candidate = dinosaur.getTarget();
        if (candidate == null || !candidate.isAlive() || shouldBite(candidate)) return false;
        target = candidate;
        return true;
    }
    @Override public boolean canContinueToUse() {
        return target != null && target == dinosaur.getTarget() && target.isAlive() && !shouldBite(target);
    }
    @Override public void start() { cooldown = 40; windup = -1; visibleTicks = 0; animationTicks = 0; }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        if (animationTicks > 0 && --animationTicks == 0)
            dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.NONE);
        boolean visible = dinosaur.getSensing().hasLineOfSight(target);
        visibleTicks = visible ? visibleTicks + 1 : 0;
        double distance = dinosaur.distanceToSqr(target);
        if (distance <= 100 && visibleTicks >= 20) dinosaur.getNavigation().stop();
        else dinosaur.getNavigation().moveTo(target, 1.0);
        dinosaur.getLookControl().setLookAt(target, 30, 30);
        if (windup >= 0) {
            if (--windup == 0) {
                dinosaur.spitVenom(target);
                cooldown = 40;
                windup = -1;
            }
        } else if (--cooldown <= 0) {
            if (distance <= 100 && visible) {
                dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.SPIT);
                animationTicks = 40;
                dinosaur.playSound(DinosaurRegistry.DILOPHOSAURUS_SPIT.get(), 1, 1);
                windup = 20;
            } else cooldown = 40;
        }
    }
    @Override public void stop() {
        dinosaur.getNavigation().stop();
        if (dinosaur.combatAnimation() == DinosaurEntity.CombatAnimation.SPIT)
            dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.NONE);
        target = null;
    }
}

final class DilophosaurusBiteGoal extends MeleeAttackGoal {
    private final DinosaurEntity dinosaur;
    DilophosaurusBiteGoal(DinosaurEntity dinosaur) { super(dinosaur, 1.0, false); this.dinosaur = dinosaur; }
    @Override public boolean canUse() { return DilophosaurusSpitGoal.shouldBite(dinosaur.getTarget()) && super.canUse(); }
    @Override public boolean canContinueToUse() { return DilophosaurusSpitGoal.shouldBite(dinosaur.getTarget()) && super.canContinueToUse(); }
}

/** The original 15-tick preparation, predictive leap, and landing attack. */
final class DinosaurLeapGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private LivingEntity target;
    private int preparation;
    private boolean finished;

    DinosaurLeapGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }
    @Override public boolean canUse() {
        LivingEntity candidate = dinosaur.getTarget();
        if (candidate == null || !candidate.isAlive() || !dinosaur.isOnGround()
                || dinosaur.herdOrNull() != null && dinosaur.herdOrNull().isFleeing()) return false;
        double distance = dinosaur.distanceToSqr(candidate);
        if (distance < 25 || distance > 36 || !dinosaur.getSensing().hasLineOfSight(candidate)) return false;
        target = candidate;
        return true;
    }
    @Override public void start() {
        finished = false;
        preparation = 0;
        dinosaur.getNavigation().stop();
        dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.PREPARE_LEAP);
    }
    @Override public boolean canContinueToUse() {
        return !finished && target != null && target == dinosaur.getTarget() && target.isAlive()
                && dinosaur.isAlive() && !dinosaur.isDinosaurSleeping();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        dinosaur.getLookControl().setLookAt(target, 30, 30);
        switch (dinosaur.combatAnimation()) {
            case PREPARE_LEAP -> {
                if (++preparation >= 15) {
                    double length = dinosaur.getBbWidth() * 6;
                    double dx = target.getX() + (target.getX() - target.xo) * length - dinosaur.getX();
                    double dz = target.getZ() + (target.getZ() - target.zo) * length - dinosaur.getZ();
                    double dy = Math.min(.3, Math.max(0, (target.getY() - dinosaur.getY()) * .1)) + .6;
                    dinosaur.setDeltaMovement(new Vec3(dx / length, dy, dz / length));
                    dinosaur.hasImpulse = true;
                    var sound = DinosaurRegistry.sound(dinosaur.species(), "attack");
                    if (sound != null) dinosaur.playSound(sound, 1, 1);
                    dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.LEAP);
                }
            }
            case LEAP -> {
                if (dinosaur.getDeltaMovement().y < 0) dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.LEAP_LAND);
            }
            case LEAP_LAND -> {
                if (dinosaur.isOnGround() || dinosaur.isInWater()) {
                    if (dinosaur.getBoundingBox().intersects(target.getBoundingBox().inflate(2))) dinosaur.doHurtTarget(target);
                    finished = true;
                }
            }
            default -> finished = true;
        }
    }
    @Override public void stop() { dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.NONE); target = null; }
}
