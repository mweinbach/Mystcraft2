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

/** Dinosaur action, diet and ownership checks shared by both loaders. */
public final class DinosaurActionGameTests {
    private DinosaurActionGameTests() {}

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

    @GameTest(template = "jurassicraft:empty")
    public static void meleeAnimationUsesSpeciesDurationAndSurvivesReload(GameTestHelper helper) {
        DinosaurEntity attacker = create(helper, Species.TYRANNOSAURUS);
        DinosaurEntity target = create(helper, Species.MUSSAURUS);
        target.setInvulnerable(true);
        int duration = DinosaurAnimationData.duration(Species.TYRANNOSAURUS, "ATTACKING");
        require(helper, duration > 12 && !attacker.doHurtTarget(target) && attacker.isAttackAnimating(),
                "even a blocked melee strike must start the original attack animation");
        require(helper, attacker.saveWithoutId(new CompoundTag()).getInt("AttackTicks") == duration,
                "the cue must use the species attack duration");
        for (int tick = 0; tick < 12; tick++) attacker.aiStep();
        attacker.swingTime = 0;
        attacker.doHurtTarget(target);
        CompoundTag saved = attacker.saveWithoutId(new CompoundTag());
        require(helper, attacker.isAttackAnimating() && saved.getInt("AttackTicks") == duration - 12,
                "the cue must outlast vanilla swingTime without restarting on a repeated strike");
        DinosaurEntity restored = create(helper, Species.TYRANNOSAURUS);
        restored.load(saved);
        require(helper, restored.isAttackAnimating() && restored.attackAnimationTime(restored.tickCount) == 12,
                "a restored or newly tracking client must seek to the saved attack frame");
        for (int tick = 0; tick < duration - 13; tick++) restored.aiStep();
        require(helper, restored.isAttackAnimating(), "the cue must last until its final species frame");
        restored.aiStep();
        require(helper, !restored.isAttackAnimating(), "the cue must end after its remaining saved ticks");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void meleeAnimationYieldsToOtherActions(GameTestHelper helper) {
        DinosaurEntity attacker = create(helper, Species.DILOPHOSAURUS);
        DinosaurEntity target = create(helper, Species.MUSSAURUS);
        target.setInvulnerable(true);
        attacker.setOnGround(true);
        attacker.doHurtTarget(target);
        require(helper, attacker.isAttackAnimating() && !attacker.canRestNaturally()
                && !attacker.playPose("EATING", 0, false), "idle rest and poses must not hide an attack");
        require(helper, !attacker.playPose("NO_SUCH_ANIMATION", 0, true) && attacker.isAttackAnimating(),
                "an invalid forced pose must not interrupt an attack");
        require(helper, attacker.playPose("EATING", 0, true) && !attacker.isAttackAnimating(),
                "a later forced pose must interrupt the attack");
        attacker.doHurtTarget(target);
        require(helper, attacker.isForcedPose() && !attacker.isAttackAnimating(), "a melee strike must not override a forced pose");
        require(helper, attacker.hurt(DamageSource.GENERIC, 1) && attacker.isInjuryAnimating(),
                "a hit must interrupt the forced pose");
        attacker.doHurtTarget(target);
        require(helper, attacker.isAttackAnimating() && !attacker.isInjuryAnimating(), "a later strike must replace injury");
        attacker.invulnerableTime = 0;
        require(helper, attacker.hurt(DamageSource.GENERIC, 1) && attacker.isInjuryAnimating() && !attacker.isAttackAnimating(),
                "a later injury must interrupt attack");
        attacker.setCombatAnimation(DinosaurEntity.CombatAnimation.SPIT);
        attacker.doHurtTarget(target);
        require(helper, !attacker.isAttackAnimating() && attacker.combatAnimation() == DinosaurEntity.CombatAnimation.SPIT,
                "melee must not replace an active special attack");
        attacker.setCombatAnimation(DinosaurEntity.CombatAnimation.NONE);
        attacker.doHurtTarget(target);
        attacker.setNaturalSleep(true);
        attacker.doHurtTarget(target);
        require(helper, !attacker.isAttackAnimating() && attacker.isDinosaurSleeping(), "sleep must end and suppress the attack");
        attacker.setNaturalSleep(false);
        attacker.doHurtTarget(target);
        attacker.setOwner(UUID.randomUUID());
        attacker.setOrder(DinosaurEntity.Order.SIT);
        attacker.doHurtTarget(target);
        require(helper, !attacker.isAttackAnimating() && attacker.isOrderedToSit(), "owner-sit must end and suppress the attack");
        DinosaurEntity unsupported = create(helper, Species.GALLIMIMUS);
        unsupported.doHurtTarget(target);
        require(helper, !unsupported.isAttackAnimating(), "species without an original attack pose must not start one");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void meleeAnimationDelaysBirthAndSurvivesReload(GameTestHelper helper) {
        DinosaurEntity mother = create(helper, Species.MUSSAURUS);
        DinosaurEntity father = create(helper, Species.MUSSAURUS);
        mother.setMale(false); father.setMale(true);
        require(helper, mother.forceBreedWith(father), "test pair must breed");
        CompoundTag data = mother.saveWithoutId(new CompoundTag());
        data.putInt("GestationTicks", 1);
        mother.load(data);
        father.setInvulnerable(true);
        mother.doHurtTarget(father);
        mother.aiStep();
        data = mother.saveWithoutId(new CompoundTag());
        require(helper, mother.isAttackAnimating() && !mother.isBirthing() && mother.isPregnant(),
                "a pending birth must wait for the active attack");
        DinosaurEntity restored = create(helper, Species.MUSSAURUS);
        restored.load(data);
        int remaining = data.getInt("AttackTicks");
        for (int tick = 0; tick < remaining - 1; tick++) restored.aiStep();
        require(helper, restored.isAttackAnimating() && !restored.isBirthing() && restored.isPregnant(),
                "a restored attack must keep birth pending");
        restored.aiStep();
        require(helper, !restored.isAttackAnimating() && restored.isBirthing() && restored.isPregnant(),
                "birth must begin after the attack ends without early delivery");
        restored.doHurtTarget(father);
        require(helper, restored.isBirthing() && !restored.isAttackAnimating(), "a melee strike must not interrupt an active birth");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void registeredSpeciesPreserveTraitsAndOwner(GameTestHelper helper) {
        for (Species species : Species.values()) {
            DinosaurEntity animal = create(helper, species);
            var attributes = new ports.jurassicraft.genetics.DinosaurAttributes(1.1f, .9f, 1.05f, .95f);
            animal.setGeneticAttributes(attributes);
            animal.setGenetics("ACGTACGTAC");
            animal.setDNAQuality(73);
            animal.setMale(true);
            animal.setBaby(true);
            if (animal.isImprintable()) animal.setOwner(java.util.UUID.fromString("85e97ce5-458b-4f0a-9ab5-f50cc86da09a"));
            CompoundTag saved = animal.saveWithoutId(new CompoundTag());
            DinosaurEntity restored = create(helper, species);
            restored.load(saved);
            require(helper, restored.species() == species && restored.isBaby() && restored.isMale()
                    && restored.getGenetics().equals("ACGTACGTAC") && restored.getDNAQuality() == 73
                    && restored.geneticAttributes().equals(attributes)
                    && java.util.Objects.equals(restored.getOwnerUUID(), animal.getOwnerUUID()),
                    "species, growth, sex, DNA, traits and owner must survive save/reload for " + species);
            require(helper, net.minecraft.world.entity.SpawnPlacements.getPlacementType(DinosaurRegistry.type(species))
                    == (species == Species.COELACANTH ? net.minecraft.world.entity.SpawnPlacements.Type.IN_WATER
                    : net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND),
                    "species must have its correct spawn placement: " + species);
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void ownerControlsAndFishDiets(GameTestHelper helper) {
        DinosaurEntity animal = create(helper, Species.TRICERATOPS);
        var owner = helper.makeMockPlayer();
        var other = helper.makeMockPlayer();
        animal.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0, 0);
        animal.setOwner(owner.getUUID());
        DinosaurOrderMenu menu = new DinosaurOrderMenu(1, owner.getInventory(), animal);
        require(helper, !menu.clickMenuButton(other, DinosaurEntity.Order.SIT.ordinal()),
                "another player must not change the order");
        require(helper, menu.clickMenuButton(owner, DinosaurEntity.Order.SIT.ordinal()) && animal.isOrderedToSit(),
                "the imprint owner should be able to order a dinosaur to sit");
        require(helper, !animal.isFood(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COD))
                && !animal.isFood(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_SALMON))
                && animal.isFood(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS)),
                "land herbivore diets should accept plants but reject fish");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void juvenileTemptationTracksDietAgeAndOrders(GameTestHelper helper) {
        DinosaurEntity juvenile = create(helper, Species.TRICERATOPS);
        juvenile.setBaby(true);
        var player = helper.makeMockPlayer();
        player.moveTo(juvenile.getX() + 4, juvenile.getY(), juvenile.getZ(), 0, 0);
        var goal = new TemptJuvenileDinosaurGoal(juvenile);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF));
        require(helper, !goal.consider(player), "juveniles must ignore food outside their diet");
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
        require(helper, goal.consider(player), "a well-fed juvenile must follow edible food in either hand");
        goal.start();
        require(helper, goal.canContinueToUse(), "the selected player should remain a valid lure");
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        require(helper, !goal.canContinueToUse(), "putting away the food must stop following");
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
        require(helper, goal.consider(player), "the juvenile can be tempted again");
        goal.start();
        player.moveTo(player.getX() + .2, player.getY(), player.getZ(), 0, 0);
        require(helper, !goal.canContinueToUse(), "a sudden nearby movement must startle a herbivore");

        CompoundTag halfGrown = juvenile.saveWithoutId(new CompoundTag());
        halfGrown.putInt("Age", -DinosaurProfile.of(juvenile.species()).growthTicks() / 2);
        juvenile.load(halfGrown);
        require(helper, juvenile.growth() == .5f && !goal.consider(player),
                "temptation must end at half of adult growth");
        juvenile.setBaby(true);
        juvenile.setOwner(player.getUUID());
        juvenile.setOrder(DinosaurEntity.Order.FOLLOW);
        require(helper, !goal.consider(player), "food must not override the owner's follow order");
        juvenile.setOrder(DinosaurEntity.Order.SIT);
        require(helper, !goal.consider(player), "food must not override the owner's sit order");

        DinosaurEntity raptor = create(helper, Species.VELOCIRAPTOR);
        raptor.setBaby(true);
        player.moveTo(raptor.getX() + 4, raptor.getY(), raptor.getZ(), 0, 0);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ports.jurassicraft.bugs.BugRegistry.CRICKETS.get()));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        var raptorGoal = new TemptJuvenileDinosaurGoal(raptor);
        require(helper, raptorGoal.consider(player), "infant carnivores should follow their insect food");
        raptorGoal.start();
        player.moveTo(player.getX() + .2, player.getY(), player.getZ(), 0, 0);
        require(helper, raptorGoal.canContinueToUse(), "meat-eating species should not startle at player movement");
        CompoundTag quarterGrown = raptor.saveWithoutId(new CompoundTag());
        quarterGrown.putInt("Age", -DinosaurProfile.of(raptor.species()).growthTicks() * 3 / 4);
        raptor.load(quarterGrown);
        require(helper, raptor.growth() == .25f && !raptorGoal.canContinueToUse(),
                "the lure must stop when insects leave the juvenile's diet");
        helper.succeed();
    }
}
