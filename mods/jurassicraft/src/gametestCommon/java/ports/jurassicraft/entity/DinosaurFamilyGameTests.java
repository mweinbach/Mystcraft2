package ports.jurassicraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.hatching.HatcheryRegistry;

public final class DinosaurFamilyGameTests {
    private DinosaurFamilyGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
    private static DinosaurEntity create(GameTestHelper helper, Species species, boolean male, boolean baby, int x, int z) {
        DinosaurEntity animal = DinosaurRegistry.type(species).create(helper.getLevel());
        if (animal == null) throw new IllegalStateException("Could not create test dinosaur");
        BlockPos pos = helper.absolutePos(new BlockPos(x, 2, z));
        animal.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        animal.setMale(male); animal.setBaby(baby);
        return animal;
    }
    private static CompoundTag relationship(DinosaurEntity other, int score) {
        CompoundTag tag = new CompoundTag();
        CompoundTag relation = new CompoundTag(); relation.putUUID("UUID", other.getUUID()); relation.putInt("Score", score);
        ListTag list = new ListTag(); list.add(relation); tag.put("Relationships", list);
        return tag;
    }

    @GameTest(template = "jurassicraft:feeder_test")
    public static void friendshipAndFamilyBoundaries(GameTestHelper helper) {
        DinosaurEntity mother = create(helper, Species.MUSSAURUS, false, false, 1, 1);
        DinosaurEntity father = create(helper, Species.MUSSAURUS, true, false, 2, 1);
        DinosaurEntity stranger = create(helper, Species.MUSSAURUS, true, false, 3, 1);
        DinosaurEntity baby = create(helper, Species.MUSSAURUS, false, true, 1, 2);
        DinosaurFamilyBrain brain = new DinosaurFamilyBrain(mother);
        brain.load(relationship(father, 900));
        require(helper, !brain.tryBond(father), "friendship must exceed 90% before a family forms");
        brain.load(relationship(father, 901));
        require(helper, brain.tryBond(father), "a sufficiently close relationship should form a family");
        CompoundTag savedRelationships = new CompoundTag(); brain.save(savedRelationships);
        require(helper, savedRelationships.getList("Relationships", 10).getCompound(0).getInt("Score") == 1000,
                "family friendship must persist at the maximum score");
        DinosaurFamilyData data = DinosaurFamilyData.get(helper.getLevel());
        DinosaurFamilyData.Family family = data.family(mother.getUUID());
        require(helper, family != null && family == data.family(father.getUUID()), "both parents share one family");
        require(helper, !data.mayBreed(mother, stranger), "a parent cannot switch mates");
        data.addChild(family.id, baby);
        BlockPos oldHome = mother.blockPosition().offset(100, 0, 0);
        data.setHome(family, oldHome, 1000);
        data.reanchorIfNeeded(baby, family);
        require(helper, family.home.equals(oldHome), "children must not individually move the family's home");
        data.reanchorIfNeeded(mother, family);
        require(helper, family.home.equals(mother.blockPosition()), "the family head should reanchor a distant home");
        require(helper, !data.mayBreed(mother, father), "tracked juveniles block another Mussaurus clutch");
        require(helper, !data.mayBreed(baby, father), "children cannot breed with family parents");
        DinosaurFamilyData restored = DinosaurFamilyData.load(data.save(new CompoundTag()));
        DinosaurFamilyData.Family restoredFamily = restored.family(mother.getUUID());
        require(helper, restoredFamily != null && restoredFamily.children.contains(baby.getUUID()), "family membership must save");
        restored.prune(helper.getLevel(), restoredFamily);
        require(helper, restoredFamily.children.contains(baby.getUUID()) && restoredFamily.parents.contains(father.getUUID()),
                "members absent from loaded chunks must not be pruned");
        data.remove(baby.getUUID());
        require(helper, data.mayBreed(mother, father), "another clutch is allowed after the young leave the family");
        data.remove(mother.getUUID());
        require(helper, data.family(father.getUUID()) != null, "a surviving parent keeps the family during gestation");
        data.remove(father.getUUID());
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:feeder_test", timeoutTicks = 40)
    public static void unloadedYoungRemainUntilMature(GameTestHelper helper) {
        var level = helper.getLevel();
        DinosaurEntity mother = create(helper, Species.MUSSAURUS, false, false, 1, 1);
        DinosaurEntity father = create(helper, Species.MUSSAURUS, true, false, 2, 1);
        DinosaurEntity baby = create(helper, Species.MUSSAURUS, false, true, 1, 2);
        level.addFreshEntity(mother); level.addFreshEntity(father); level.addFreshEntity(baby);
        DinosaurFamilyData data = DinosaurFamilyData.get(level);
        DinosaurFamilyData.Family family = data.form(mother, father);
        require(helper, family != null, "parents should form a family");
        data.addChild(family.id, baby);
        CompoundTag savedBaby = baby.saveWithoutId(new CompoundTag());
        helper.runAfterDelay(2, () -> {
            baby.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.runAfterDelay(2, () -> {
                data.prune(level, family);
                require(helper, family.children.contains(baby.getUUID()) && !data.mayBreed(mother, father),
                        "unloading a living juvenile must not permit an early clutch");
                DinosaurEntity reloaded = DinosaurRegistry.type(Species.MUSSAURUS).create(level);
                if (reloaded == null) { helper.fail("could not reload juvenile"); return; }
                reloaded.load(savedBaby);
                require(helper, level.addFreshEntity(reloaded), "juvenile should reload with the same UUID");
                helper.runAfterDelay(2, () -> {
                    reloaded.setBaby(false);
                    data.prune(level, family);
                    require(helper, !family.children.contains(reloaded.getUUID()) && data.mayBreed(mother, father),
                            "a loaded juvenile leaves its family after passing half growth");
                    reloaded.discard(); mother.discard(); father.discard();
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "jurassicraft:feeder_test", timeoutTicks = 30)
    public static void pickedUpNaturalEggKeepsFamily(GameTestHelper helper) {
        DinosaurEntity mother = create(helper, Species.MUSSAURUS, false, false, 1, 1);
        DinosaurEntity father = create(helper, Species.MUSSAURUS, true, false, 2, 1);
        DinosaurFamilyData data = DinosaurFamilyData.get(helper.getLevel());
        DinosaurFamilyData.Family family = data.form(mother, father);
        require(helper, family != null, "parents should form a family");
        father.discard();
        DinosaurFamilyData loadedFamilies = DinosaurFamilyData.load(data.save(new CompoundTag()));
        require(helper, loadedFamilies.family(mother.getUUID()) != null,
                "a family must survive the father's death before the egg hatches and a save/load");
        DinosaurEggEntity egg = DinosaurRegistry.EGG.get().create(helper.getLevel());
        if (egg == null) { helper.fail("could not create egg"); return; }
        egg.setEmbryo(Species.MUSSAURUS, "AAAAAAAAAA", 50, false);
        egg.setFamily(family.id);
        CompoundTag serialized = egg.saveWithoutId(new CompoundTag());
        DinosaurEggEntity restored = DinosaurRegistry.EGG.get().create(helper.getLevel());
        if (restored == null) { helper.fail("could not restore egg"); return; }
        restored.load(serialized);
        var player = helper.makeMockPlayer();
        player.getAbilities().mayBuild = true;
        restored.interact(player, InteractionHand.MAIN_HAND);
        ItemStack stack = player.getInventory().items.stream()
                .filter(item -> item.is(HatcheryRegistry.hatchedEgg(Species.MUSSAURUS))).findFirst().orElse(ItemStack.EMPTY);
        require(helper, !stack.isEmpty() && stack.getTag().hasUUID("Family") && stack.getTag().getUUID("Family").equals(family.id),
                "egg save and pickup must retain the family link");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.setBlock(2, 1, 2, Blocks.STONE);
        BlockPos support = helper.absolutePos(new BlockPos(2, 1, 2));
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false)));
        require(helper, result.consumesAction() && family.children.size() == 1,
                "using the picked-up egg must join the newborn to its family");
        var childId = family.children.iterator().next();
        helper.runAfterDelay(2, () -> {
            Entity child = helper.getLevel().getEntity(childId);
            require(helper, child instanceof DinosaurEntity, "the newborn must be loaded");
            child.discard(); data.remove(mother.getUUID()); data.remove(father.getUUID());
            helper.succeed();
        });
    }

    @GameTest(template = "jurassicraft:feeder_test", timeoutTicks = 30)
    public static void familySurvivesDeathDuringGestation(GameTestHelper helper) {
        var level = helper.getLevel();
        DinosaurEntity mother = create(helper, Species.MUSSAURUS, false, false, 1, 1);
        DinosaurEntity father = create(helper, Species.MUSSAURUS, true, false, 2, 1);
        DinosaurFamilyData data = DinosaurFamilyData.get(level);
        DinosaurFamilyData.Family family = data.form(mother, father);
        require(helper, family != null && mother.forceBreedWith(father), "parents should conceive");
        father.discard();
        CompoundTag familySave = data.save(new CompoundTag());
        require(helper, DinosaurFamilyData.load(familySave).family(mother.getUUID()) != null,
                "the surviving mother's family must be saved while pregnant");
        CompoundTag motherSave = mother.saveWithoutId(new CompoundTag());
        DinosaurEntity restored = DinosaurRegistry.type(Species.MUSSAURUS).create(level);
        if (restored == null) { helper.fail("could not restore mother"); return; }
        restored.load(motherSave);
        require(helper, restored.deliverPregnancyNow(), "the reloaded mother should deliver her clutch");
        helper.runAfterDelay(2, () -> {
            var eggs = level.getEntitiesOfClass(DinosaurEggEntity.class, restored.getBoundingBox().inflate(5),
                    egg -> egg.saveWithoutId(new CompoundTag()).hasUUID("Family")
                            && egg.saveWithoutId(new CompoundTag()).getUUID("Family").equals(family.id));
            require(helper, !eggs.isEmpty(), "eggs laid after a mate dies must retain the family link");
            eggs.forEach(Entity::discard); data.remove(mother.getUUID());
            helper.succeed();
        });
    }

    @GameTest(template = "jurassicraft:feeder_test", timeoutTicks = 70)
    public static void naturalHatchJoinsUnloadedFamily(GameTestHelper helper) {
        var level = helper.getLevel();
        DinosaurEntity mother = create(helper, Species.MUSSAURUS, false, false, 1, 1);
        DinosaurEntity father = create(helper, Species.MUSSAURUS, true, false, 2, 1);
        DinosaurFamilyData data = DinosaurFamilyData.get(level);
        DinosaurFamilyData.Family family = data.form(mother, father);
        require(helper, family != null, "parents should form a family");
        DinosaurEggEntity egg = DinosaurRegistry.EGG.get().create(level);
        if (egg == null) { helper.fail("could not create egg"); return; }
        egg.setEmbryo(Species.MUSSAURUS, "AAAAAAAAAA", 40, false);
        egg.setFamily(family.id);
        helper.setBlock(2, 1, 2, Blocks.STONE);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        egg.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        CompoundTag eggSave = egg.saveWithoutId(new CompoundTag());
        eggSave.putInt("HatchTime", 0);
        DinosaurEggEntity restored = DinosaurRegistry.EGG.get().create(level);
        if (restored == null) { helper.fail("could not reload egg"); return; }
        restored.load(eggSave);
        require(helper, level.addFreshEntity(restored), "reloaded egg should enter the world");
        helper.runAfterDelay(30, () -> {
            require(helper, family.children.size() == 1, "a naturally hatched baby must join even while parents are unloaded");
            var childId = family.children.iterator().next();
            Entity child = level.getEntity(childId);
            require(helper, child instanceof DinosaurEntity dinosaur && dinosaur.getDNAQuality() == 40,
                    "the joined hatchling should retain its inherited quality");
            child.discard(); data.remove(mother.getUUID()); data.remove(father.getUUID());
            helper.succeed();
        });
    }

    @GameTest(template = "jurassicraft:feeder_test", timeoutTicks = 30)
    public static void adultsDefendAttackedInfants(GameTestHelper helper) {
        var level = helper.getLevel();
        DinosaurEntity adult = create(helper, Species.MUSSAURUS, false, false, 1, 1);
        DinosaurEntity infant = create(helper, Species.MUSSAURUS, false, true, 2, 1);
        Chicken attacker = EntityType.CHICKEN.create(level);
        if (attacker == null) { helper.fail("could not create attacker"); return; }
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 1));
        attacker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        level.addFreshEntity(adult); level.addFreshEntity(infant); level.addFreshEntity(attacker);
        helper.runAfterDelay(2, () -> {
            require(helper, infant.hurt(DamageSource.mobAttack(attacker), 1), "attack should reach the juvenile");
            require(helper, adult.getTarget() == attacker, "nearby adult should respond to an attacked infant");
            adult.setTarget(null);
            infant.defendInfant(attacker);
            DinosaurFamilyBrain.assistYoung(adult);
            require(helper, adult.getTarget() == attacker, "adults should also assist an unrelated infant's existing target");
            adult.setTarget(null);
            infant.setBaby(false);
            DinosaurFamilyBrain.defendYoung(infant, attacker);
            require(helper, adult.getTarget() == null, "adult animals do not trigger infant protection");
            adult.discard(); infant.discard(); attacker.discard();
            helper.succeed();
        });
    }
}
