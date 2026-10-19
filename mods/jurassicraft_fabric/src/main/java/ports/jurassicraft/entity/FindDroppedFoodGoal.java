package ports.jurassicraft.entity;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;

/** Hungry dinosaurs seek edible dropped items and eat one at a time. */
final class FindDroppedFoodGoal extends Goal {
    private final DinosaurEntity dinosaur;
    private ItemEntity food;
    private int pathUpdate;
    private int nextSearch;
    private int elapsed;
    private boolean pathFailed;

    FindDroppedFoodGoal(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (!canEat() || dinosaur.tickCount < nextSearch) return false;
        nextSearch = dinosaur.tickCount + 20;
        food = dinosaur.level.getEntitiesOfClass(ItemEntity.class, dinosaur.getBoundingBox().inflate(16),
                item -> item.isAlive() && dinosaur.isFood(item.getItem())).stream()
                .min(Comparator.comparingDouble(dinosaur::distanceToSqr)).orElse(null);
        return food != null;
    }

    @Override public boolean canContinueToUse() {
        return canEat() && food != null && food.isAlive() && dinosaur.isFood(food.getItem())
                && !pathFailed && elapsed < 600 && dinosaur.distanceToSqr(food) < 1024;
    }

    @Override public void start() { pathUpdate = elapsed = 0; pathFailed = false; }

    @Override public void tick() {
        if (!canContinueToUse()) return;
        elapsed++;
        dinosaur.getLookControl().setLookAt(food);
        if (dinosaur.getBoundingBox().inflate(1).intersects(food.getBoundingBox())) {
            ItemStack stack = food.getItem().copy();
            if (dinosaur.feed(stack)) {
                stack.shrink(1);
                if (stack.isEmpty()) food.discard();
                else food.setItem(stack);
                dinosaur.playSound(SoundEvents.GENERIC_EAT, 1, 1);
            }
        } else if (pathUpdate-- <= 0) {
            pathUpdate = 10;
            pathFailed = !dinosaur.getNavigation().moveTo(food, 1.0);
        }
    }

    @Override public void stop() {
        dinosaur.getNavigation().stop();
        food = null;
    }

    private boolean canEat() {
        return dinosaur.isAlive() && dinosaur.needsFood() && !dinosaur.isDinosaurSleeping() && dinosaur.getTarget() == null;
    }
}
