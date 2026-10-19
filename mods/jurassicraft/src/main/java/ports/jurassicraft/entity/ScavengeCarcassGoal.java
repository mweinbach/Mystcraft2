package ports.jurassicraft.entity;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;

/** Hungry carnivores approach carcasses and consume one serving from each bite. */
final class ScavengeCarcassGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private DinosaurEntity carcass;
    private int elapsed;

    ScavengeCarcassGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (!dinosaur.isAlive() || !dinosaur.needsFood() || dinosaur.isDinosaurSleeping()
                || dinosaur.getTarget() != null || dinosaur.tickCount % 20 != 0) return false;
        carcass = dinosaur.level.getEntitiesOfClass(DinosaurEntity.class, dinosaur.getBoundingBox().inflate(16),
                candidate -> candidate != dinosaur && candidate.isCarcass() && !candidate.isRemoved()).stream()
                .min(Comparator.comparingDouble(dinosaur::distanceToSqr)).orElse(null);
        return carcass != null;
    }

    @Override public void start() {
        elapsed = 0;
        dinosaur.getNavigation().moveTo(carcass, 1.1);
    }

    @Override public boolean canContinueToUse() {
        return carcass != null && carcass.isCarcass() && !carcass.isRemoved() && dinosaur.isAlive()
                && dinosaur.needsFood() && !dinosaur.isDinosaurSleeping() && dinosaur.getTarget() == null
                && dinosaur.distanceToSqr(carcass) < 1024 && elapsed < 600;
    }

    @Override public void tick() {
        elapsed++;
        dinosaur.getLookControl().setLookAt(carcass);
        double reach = Math.max(2, (dinosaur.getBbWidth() + carcass.getBbWidth()) / 2 + 1);
        if (dinosaur.distanceToSqr(carcass) <= reach * reach) {
            if (elapsed % 20 == 1 && carcass.hurt(DamageSource.mobAttack(dinosaur), 1)) dinosaur.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        } else if (elapsed % 20 == 1) dinosaur.getNavigation().moveTo(carcass, 1.1);
    }

    @Override public void stop() {
        dinosaur.getNavigation().stop();
        carcass = null;
    }
}
