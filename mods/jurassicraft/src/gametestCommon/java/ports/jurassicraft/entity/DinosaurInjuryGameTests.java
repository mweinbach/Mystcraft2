package ports.jurassicraft.entity;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.content.Species;

public final class DinosaurInjuryGameTests {
    private DinosaurInjuryGameTests() {}

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private static DinosaurEntity create(GameTestHelper helper, Species species) {
        DinosaurEntity dinosaur = DinosaurRegistry.type(species).create(helper.getLevel());
        if (dinosaur == null) throw new IllegalStateException("Could not create dinosaur");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        dinosaur.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        dinosaur.setBaby(false);
        return dinosaur;
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void injuryUsesSpeciesDurationAndSurvivesReload(GameTestHelper helper) {
        DinosaurEntity dinosaur = create(helper, Species.TYRANNOSAURUS);
        int duration = DinosaurAnimationData.duration(Species.TYRANNOSAURUS, "INJURED");
        require(helper, duration > 10 && dinosaur.hurt(DamageSource.GENERIC, 1), "nonlethal damage should be accepted");
        require(helper, dinosaur.isInjuryAnimating() && dinosaur.saveWithoutId(new CompoundTag()).getInt("InjuryTicks") == duration,
                "injury must start with the imported species animation duration");
        for (int tick = 0; tick < 12; tick++) dinosaur.aiStep();
        dinosaur.hurtTime = 0;
        CompoundTag saved = dinosaur.saveWithoutId(new CompoundTag());
        require(helper, dinosaur.isInjuryAnimating() && saved.getInt("InjuryTicks") == duration - 12,
                "the injury cue must outlast vanilla hurtTime");
        DinosaurEntity restored = create(helper, Species.TYRANNOSAURUS);
        restored.load(saved);
        require(helper, restored.isInjuryAnimating() && restored.injuryAnimationTime(restored.tickCount) == 12,
                "a newly tracked restored dinosaur should seek to the saved frame");
        for (int tick = 0; tick < duration - 13; tick++) restored.aiStep();
        require(helper, restored.isInjuryAnimating(), "the cue must last until its final species frame");
        restored.aiStep();
        require(helper, !restored.isInjuryAnimating(), "the cue must end after the remaining saved ticks");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void injuryRejectsBlockedHitsAndYieldsToNewActions(GameTestHelper helper) {
        DinosaurEntity dinosaur = create(helper, Species.DILOPHOSAURUS);
        dinosaur.setOnGround(true);
        require(helper, dinosaur.canRestNaturally(), "fixture should otherwise be eligible for natural rest");
        dinosaur.setInvulnerable(true);
        require(helper, !dinosaur.hurt(DamageSource.GENERIC, 1) && !dinosaur.isInjuryAnimating(),
                "blocked damage must not start an injury cue");
        dinosaur.setInvulnerable(false);
        require(helper, dinosaur.hurt(DamageSource.GENERIC, 1) && dinosaur.isInjuryAnimating(),
                "accepted damage must start an injury cue");
        require(helper, !dinosaur.canRestNaturally(), "idle rest must not hide an active injury");
        for (int tick = 0; tick < 3; tick++) dinosaur.aiStep();
        int remaining = dinosaur.saveWithoutId(new CompoundTag()).getInt("InjuryTicks");
        dinosaur.hurt(DamageSource.GENERIC, 1);
        require(helper, dinosaur.saveWithoutId(new CompoundTag()).getInt("InjuryTicks") == remaining,
                "a hit blocked by hurt resistance must not restart the cue");
        require(helper, !dinosaur.playPose("NO_SUCH_ANIMATION", 0, true) && dinosaur.isInjuryAnimating(),
                "an invalid forced pose must not interrupt injury");
        require(helper, !dinosaur.playPose("EATING", 0, false), "ordinary poses must wait for injury");
        require(helper, dinosaur.playPose("EATING", 0, true) && !dinosaur.isInjuryAnimating(),
                "a later forced pose must interrupt injury");
        dinosaur.invulnerableTime = 0;
        require(helper, dinosaur.hurt(DamageSource.GENERIC, 1) && dinosaur.isInjuryAnimating() && !dinosaur.isForcedPose(),
                "a later accepted hit must interrupt the forced pose");
        dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.SPIT);
        require(helper, !dinosaur.isInjuryAnimating() && dinosaur.combatAnimation() == DinosaurEntity.CombatAnimation.SPIT,
                "a later combat action must interrupt injury");
        dinosaur.invulnerableTime = 0;
        require(helper, dinosaur.hurt(DamageSource.GENERIC, 1) && dinosaur.isInjuryAnimating()
                && dinosaur.isCombatPoseInterrupted() && dinosaur.combatAnimation() == DinosaurEntity.CombatAnimation.SPIT,
                "a later injury should interrupt the old combat pose without cancelling its goal");
        dinosaur.setCombatAnimation(DinosaurEntity.CombatAnimation.SPIT);
        require(helper, !dinosaur.isInjuryAnimating() && !dinosaur.isCombatPoseInterrupted(),
                "a newly started combat phase must become visible again");
        DinosaurEntity lethal = new DinosaurEntity(DinosaurRegistry.type(Species.DILOPHOSAURUS), helper.getLevel()) {
            @Override boolean allowsCarcass() { return true; }
        };
        lethal.setBaby(false);
        require(helper, lethal.hurt(DamageSource.GENERIC, 100000) && lethal.isCarcass() && !lethal.isInjuryAnimating(),
                "a lethal hit must not leave an injury cue on the carcass");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void injuryPausesAndRestoresBirthWithoutEarlyDelivery(GameTestHelper helper) {
        DinosaurEntity mother = create(helper, Species.MUSSAURUS);
        DinosaurEntity father = create(helper, Species.MUSSAURUS);
        mother.setMale(false); father.setMale(true);
        require(helper, mother.forceBreedWith(father), "test pair must breed");
        CompoundTag data = mother.saveWithoutId(new CompoundTag());
        data.putInt("GestationTicks", 1);
        mother.load(data);
        mother.aiStep();
        for (int tick = 0; tick < 5; tick++) mother.aiStep();
        int progress = mother.saveWithoutId(new CompoundTag()).getInt("BirthElapsed");
        require(helper, mother.isBirthing() && mother.hurt(DamageSource.GENERIC, .5f), "damage should interrupt active birth");
        for (int tick = 0; tick < 5; tick++) mother.aiStep();
        data = mother.saveWithoutId(new CompoundTag());
        require(helper, mother.isInjuryAnimating() && !mother.isBirthing() && data.getInt("BirthElapsed") == progress && mother.isPregnant(),
                "injury must pause birth and retain its embryos");
        DinosaurEntity restored = create(helper, Species.MUSSAURUS);
        restored.load(data);
        require(helper, restored.isInjuryAnimating() && restored.isPregnant(), "reload must retain both injury and pregnancy");
        int remaining = data.getInt("InjuryTicks");
        for (int tick = 0; tick < remaining - 1; tick++) restored.aiStep();
        require(helper, !restored.isBirthing() && restored.saveWithoutId(new CompoundTag()).getInt("BirthElapsed") == progress,
                "restored injury must keep birth paused");
        restored.aiStep();
        require(helper, !restored.isInjuryAnimating() && restored.isBirthing()
                && restored.saveWithoutId(new CompoundTag()).getInt("BirthElapsed") == progress,
                "birth must resume at the exact saved frame");
        AABB area = new AABB(restored.blockPosition()).inflate(5);
        Set<UUID> existing = helper.getLevel().getEntitiesOfClass(DinosaurEggEntity.class, area).stream()
                .map(Entity::getUUID).collect(Collectors.toSet());
        try {
            int midpoint = DinosaurAnimationData.duration(Species.MUSSAURUS, "LAYING_EGG") / 2;
            for (int tick = progress; tick < midpoint; tick++) restored.aiStep();
            require(helper, !restored.isPregnant() && helper.getLevel().getEntitiesOfClass(DinosaurEggEntity.class, area,
                    egg -> !existing.contains(egg.getUUID())).size() > 0,
                    "the resumed birth must deliver at its midpoint");
        } finally {
            helper.getLevel().getEntitiesOfClass(DinosaurEggEntity.class, area,
                    egg -> !existing.contains(egg.getUUID())).forEach(Entity::discard);
        }
        helper.succeed();
    }
}
