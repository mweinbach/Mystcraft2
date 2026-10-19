package com.technovision.advancedgenetics.gametest;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.commands.GeneArgumentType;
import com.technovision.advancedgenetics.common.entity.FireballEntity;
import com.technovision.advancedgenetics.common.goal.FleePlayerGoal;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.mixin.MobAccessor;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.genetics;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.getField;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.platform;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.player;

public class GeneGameTests {

    @GameTest(template = TEMPLATE)
    public static void playerGeneticsPersistGenesAndSkipUnknownNames(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ag_persist", Genes.FLIGHT, Genes.SPEED);

        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        CompoundTag genetics = platform().savedGenetics(saved);
        check(genetics != null, "Player save has no genetics: " + saved.getAllKeys());
        genetics.getCompound("genes").putString("REMOVED_GENE", "Removed Gene");

        ServerPlayer restored = player(helper, "ag_restore");
        restored.load(saved);
        PlayerGeneticsComponent genes = genetics(restored);
        check(genes.geneCount() == 2, "Expected 2 restored genes, got " + genes.getGenes());
        check(genes.hasGene(Genes.FLIGHT) && genes.hasGene(Genes.SPEED), "Restored genes differ: " + genes.getGenes());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void respawnCopiesGenesAndKeepInventoryKeepsItems(GameTestHelper helper) {
        ServerPlayer keeper = player(helper, "ag_keeper", Genes.KEEP_INVENTORY, Genes.FLIGHT);
        keeper.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        ServerPlayer keeperClone = player(helper, "ag_keeper2");
        keeperClone.restoreFrom(keeper, false);
        check(genetics(keeperClone).hasGene(Genes.KEEP_INVENTORY) && genetics(keeperClone).hasGene(Genes.FLIGHT),
                "Respawned player lost genes: " + genetics(keeperClone).getGenes());
        check(keeperClone.getInventory().countItem(Items.DIAMOND) == 3, "Keep Inventory did not restore the inventory");

        ServerPlayer plain = player(helper, "ag_plain", Genes.SPEED);
        plain.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        ServerPlayer plainClone = player(helper, "ag_plain2");
        plainClone.restoreFrom(plain, false);
        check(genetics(plainClone).hasGene(Genes.SPEED), "Respawned player lost Speed");
        check(plainClone.getInventory().isEmpty(), "Inventory was restored without Keep Inventory");

        ServerPlayer traveller = player(helper, "ag_traveller", Genes.WOOLY);
        ServerPlayer returned = player(helper, "ag_returned");
        returned.restoreFrom(traveller, true);
        check(genetics(returned).hasGene(Genes.WOOLY), "Returning from the End lost genes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void keepInventoryGeneCancelsDeathDrops(GameTestHelper helper) throws ReflectiveOperationException {
        Method dropEquipment = Player.class.getDeclaredMethod("dropEquipment");
        dropEquipment.setAccessible(true);
        BlockPos dropPos = new BlockPos(1, 2, 1);

        ServerPlayer keeper = player(helper, "ag_nodrop", Genes.KEEP_INVENTORY);
        keeper.getInventory().add(new ItemStack(Items.EMERALD, 2));
        dropEquipment.invoke(keeper);
        check(keeper.getInventory().countItem(Items.EMERALD) == 2, "Keep Inventory player dropped items");
        helper.assertItemEntityNotPresent(Items.EMERALD, dropPos, 3.0);

        ServerPlayer plain = player(helper, "ag_drop");
        plain.getInventory().add(new ItemStack(Items.EMERALD, 2));
        dropEquipment.invoke(plain);
        check(plain.getInventory().isEmpty(), "Player without Keep Inventory kept items");
        helper.assertItemEntityPresent(Items.EMERALD, dropPos, 3.0);
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void deathGenesDropEmeraldsAndSpawnSlimes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        ServerPlayer player = player(helper, "ag_dies", pos);
        genetics(player).addGene(Genes.EMERALD_HEART);
        genetics(player).addGene(Genes.SLIMY);
        player.die(DamageSource.GENERIC);
        helper.assertItemEntityPresent(Items.EMERALD, pos, 2.0);
        helper.assertEntityPresent(EntityType.SLIME, pos, 2.0);
        helper.killAllEntities();

        player.die(DamageSource.GENERIC);
        helper.assertItemEntityNotPresent(Items.EMERALD, pos, 2.0);
        helper.killAllEntities();

        ServerPlayer plain = player(helper, "ag_plain_death", pos);
        plain.die(DamageSource.GENERIC);
        helper.assertItemEntityNotPresent(Items.EMERALD, pos, 2.0);
        check(helper.getLevel().getEntitiesOfClass(Slime.class, new AABB(helper.absolutePos(pos)).inflate(2.0), Slime::isAlive).isEmpty(),
                "A player without Slimy spawned a slime");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void webWalkingGeneSkipsCobwebSlowdown(GameTestHelper helper) {
        Vec3 cobweb = new Vec3(0.25, 0.05, 0.25);

        ServerPlayer plain = player(helper, "ag_stuck");
        plain.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), cobweb);
        check(cobweb.equals(getField(Entity.class, "stuckSpeedMultiplier", plain)), "Cobweb did not slow a normal player");

        ServerPlayer walker = player(helper, "ag_web", Genes.WEB_WALKING);
        walker.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), cobweb);
        check(Vec3.ZERO.equals(getField(Entity.class, "stuckSpeedMultiplier", walker)), "Web Walking player was slowed by a cobweb");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void climbWallsGeneClimbsSolidWalls(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 2, 2);
        helper.setBlock(feet.below(), Blocks.STONE);
        helper.setBlock(feet.north(), Blocks.STONE);
        helper.setBlock(feet.north().above(), Blocks.STONE);

        ServerPlayer player = player(helper, "ag_climb", feet);
        check(!player.onClimbable(), "A stone wall is climbable without the gene");
        genetics(player).addGene(Genes.CLIMB_WALLS);
        check(player.onClimbable(), "Climb Walls player cannot climb a stone wall");

        ServerPlayer open = player(helper, "ag_open", new BlockPos(5, 2, 5));
        genetics(open).addGene(Genes.CLIMB_WALLS);
        check(!open.onClimbable(), "Climb Walls player climbs without a wall");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void damageGenesCancelMatchingAttacks(GameTestHelper helper) {
        ServerPlayer plain = player(helper, "ag_hurt");
        check(!negated(plain, DamageSource.FALL, 4.0F), "Fall damage was cancelled without a gene");

        ServerPlayer feather = player(helper, "ag_fall", Genes.NO_FALL_DAMAGE);
        check(negated(feather, DamageSource.FALL, 4.0F), "No Fall Damage did not cancel fall damage");
        check(feather.getHealth() == feather.getMaxHealth(), "No Fall Damage player was hurt");
        check(!negated(feather, DamageSource.GENERIC, 4.0F), "No Fall Damage cancelled generic damage");

        ServerPlayer immune = player(helper, "ag_poison", Genes.POISON_IMMUNITY);
        immune.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        check(negated(immune, DamageSource.MAGIC, 1.0F), "Poison Immunity did not cancel poison damage");
        check(!immune.hasEffect(MobEffects.POISON), "Poison Immunity did not clear poison");

        ServerPlayer dragon = player(helper, "ag_dragon", Genes.DRAGONS_HEALTH);
        ItemStack crystal = new ItemStack(ItemRegistry.DRAGON_HEALTH_CRYSTAL.get());
        dragon.getInventory().setItem(0, crystal);
        check(negated(dragon, DamageSource.GENERIC, 5.0F), "Dragon's Health crystal did not absorb damage");
        check(crystal.getDamageValue() == 5, "Crystal durability loss was " + crystal.getDamageValue());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serverTicksApplyFlightEffectsAndHunger(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ag_tick", Genes.FLIGHT, Genes.RESISTANCE, Genes.NO_HUNGER);
        player.getFoodData().setFoodLevel(5);
        tickSeconds(player, 4);
        check(player.getAbilities().mayfly, "Flight gene did not allow flight");
        check(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance gene did not apply its effect");
        check(player.getFoodData().getFoodLevel() == 6, "No Hunger fed to " + player.getFoodData().getFoodLevel());

        genetics(player).removeGene(Genes.FLIGHT);
        tickSeconds(player, 4);
        check(!player.getAbilities().mayfly, "Flight remained after removing the gene");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void spectatorsKeepFlightWithoutTheGene(GameTestHelper helper) {
        ServerPlayer spectator = player(helper, "ag_spectator");
        check(spectator.setGameMode(GameType.SPECTATOR), "Could not switch to spectator mode");
        check(spectator.getAbilities().mayfly && spectator.getAbilities().flying, "Spectator mode did not enable flight");
        tickSeconds(spectator, 4);
        check(spectator.getAbilities().mayfly && spectator.getAbilities().flying, "Spectator lost flight without the Flight gene");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireChargesShootWithoutTheGeneAndNotForSpectators(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ag_charge");
        shooter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIRE_CHARGE, 2));
        check(useItem(shooter) == InteractionResult.SUCCESS, "Using a fire charge was not handled");
        check(shooter.getMainHandItem().getCount() == 1, "Shooting used " + (2 - shooter.getMainHandItem().getCount()) + " charges");
        check(fireballs(helper, shooter).size() == 1, "No fireball was shot");

        ServerPlayer spectator = player(helper, "ag_ghost");
        spectator.setGameMode(GameType.SPECTATOR);
        spectator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIRE_CHARGE, 2));
        check(useItem(spectator) == InteractionResult.PASS, "A spectator's fire charge was handled");
        check(fireballs(helper, spectator).isEmpty() && spectator.getMainHandItem().getCount() == 2, "A spectator shot a fireball");

        ServerPlayer cooling = player(helper, "ag_cooldown");
        cooling.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIRE_CHARGE, 2));
        cooling.getCooldowns().addCooldown(Items.FIRE_CHARGE, 100);
        check(useItem(cooling) == InteractionResult.PASS && fireballs(helper, cooling).isEmpty(), "A fire charge on cooldown shot");

        ServerPlayer gene = player(helper, "ag_inverted", Genes.SHOOT_FIREBALLS);
        gene.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIRE_CHARGE, 2));
        useItem(gene);
        check(fireballs(helper, gene).isEmpty(), "Upstream only shoots without the gene, but a gene holder shot");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireballExplosionCreditsTheShooter(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos target = new BlockPos(4, 2, 4);
        helper.setBlock(target.below(), Blocks.STONE);
        LivingEntity chicken = helper.spawn(EntityType.CHICKEN, target);
        ServerPlayer shooter = player(helper, "ag_shooter");
        FireballEntity fireball = new FireballEntity(helper.getLevel(), shooter, new Vec3(0, -1, 0));
        check(fireball.getOwner() == shooter, "Fireball owner is " + fireball.getOwner());

        fireball.moveTo(chicken.position());
        BlockPos floor = helper.absolutePos(target.below());
        hit(fireball, new BlockHitResult(chicken.position(), Direction.UP, floor, false));
        check(fireball.isRemoved(), "Fireball survived its impact");
        check(chicken.isDeadOrDying(), "Fireball explosion did not kill the chicken");
        check(chicken.getKillCredit() == shooter, "Kill credit went to " + chicken.getKillCredit());
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireballDamagesItsDirectTargetOnce(GameTestHelper helper) {
        LivingEntity golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 4));
        ServerPlayer shooter = player(helper, "ag_burner");
        FireballEntity fireball = new FireballEntity(helper.getLevel(), shooter, new Vec3(0, -1, 0));
        fireball.moveTo(golem.getEyePosition());

        int fireballHits = platform().countDamage(golem,
                source -> source.getMsgId().equals("fireball") && source.getEntity() == shooter,
                () -> hit(fireball, new EntityHitResult(golem)));
        check(fireballHits == 1, "Shooter's fireball damaged its target " + fireballHits + " times");
        check(golem.isOnFire(), "Fireball did not ignite its target");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void witherHitGeneWithersInsteadOfHitting(GameTestHelper helper) {
        LivingEntity pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
        ServerPlayer plain = player(helper, "ag_puncher");
        plain.attack(pig);
        check(pig.getHealth() < pig.getMaxHealth(), "A normal punch did not hurt the pig");
        check(!pig.hasEffect(MobEffects.WITHER), "A normal punch withered the pig");

        LivingEntity target = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 5));
        ServerPlayer witherer = player(helper, "ag_witherer", Genes.WITHER_HIT);
        witherer.attack(target);
        check(target.hasEffect(MobEffects.WITHER), "Wither Hit did not wither the pig");
        check(target.getHealth() == target.getMaxHealth(), "Wither Hit did not cancel the punch");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void eatGrassGeneTurnsGrassToDirt(GameTestHelper helper) {
        BlockPos grass = new BlockPos(1, 1, 1);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);

        ServerPlayer plain = player(helper, "ag_nograss");
        plain.getFoodData().setFoodLevel(10);
        check(useBlock(helper, plain, grass) == InteractionResult.PASS, "Grass click was handled without the gene");
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, grass);

        ServerPlayer grazer = player(helper, "ag_grass", Genes.EAT_GRASS);
        grazer.getFoodData().setFoodLevel(10);
        check(useBlock(helper, grazer, grass) == InteractionResult.SUCCESS, "Eat Grass did not handle the click");
        helper.assertBlockPresent(Blocks.DIRT, grass);
        check(grazer.getFoodData().getFoodLevel() == 11, "Eat Grass fed to " + grazer.getFoodData().getFoodLevel());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void milkyGeneFillsOneBucketFromAStack(GameTestHelper helper) {
        ServerPlayer cow = player(helper, "ag_cow", Genes.MILKY);
        ServerPlayer milker = player(helper, "ag_milker");
        milker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));

        check(interact(milker, cow) == InteractionResult.SUCCESS, "Milky interaction was not handled");
        check(milker.getMainHandItem().is(Items.BUCKET) && milker.getMainHandItem().getCount() == 2,
                "Milking consumed the wrong bucket count: " + milker.getMainHandItem());
        check(milker.getInventory().countItem(Items.MILK_BUCKET) == 1, "Milk bucket was not added to the inventory");

        check(interact(milker, cow) == InteractionResult.SUCCESS, "Milky cooldown interaction was not handled");
        check(milker.getMainHandItem().getCount() == 2, "Milky ignored its cooldown");

        milker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        check(interact(milker, cow) == InteractionResult.PASS, "An unrelated interaction was handled");

        milker.setGameMode(GameType.SPECTATOR);
        milker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        check(interact(milker, player(helper, "ag_cow2", Genes.MILKY)) == InteractionResult.PASS, "A spectator milked a player");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void scareGenesAddFleeGoals(GameTestHelper helper) {
        Mob creeper = helper.spawn(EntityType.CREEPER, new BlockPos(1, 2, 1));
        Mob skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(3, 2, 3));
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
        check(hasFleeGoal(creeper), "Creeper has no flee goal");
        check(hasFleeGoal(skeleton), "Skeleton has no flee goal");
        check(!hasFleeGoal(zombie), "Zombie gained a flee goal");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void infinityGeneFiresWithoutUsingPlainArrows(GameTestHelper helper) {
        ServerPlayer plain = archer(helper, "ag_archer", new BlockPos(1, 2, 1), new ItemStack(Items.ARROW, 2));
        shootBow(plain);
        check(plain.getInventory().countItem(Items.ARROW) == 1, "A normal shot did not use an arrow");
        check(arrowPickup(helper, plain) == AbstractArrow.Pickup.ALLOWED, "A normal arrow cannot be picked up");

        ServerPlayer infinite = archer(helper, "ag_infinity", new BlockPos(3, 2, 1), new ItemStack(Items.ARROW, 2));
        genetics(infinite).addGene(Genes.INFINITY);
        shootBow(infinite);
        check(infinite.getInventory().countItem(Items.ARROW) == 2, "Infinity gene used a plain arrow");
        check(arrowPickup(helper, infinite) == AbstractArrow.Pickup.CREATIVE_ONLY, "An infinite arrow can be picked up");

        ServerPlayer empty = archer(helper, "ag_empty_quiver", new BlockPos(5, 2, 1), ItemStack.EMPTY);
        genetics(empty).addGene(Genes.INFINITY);
        shootBow(empty);
        check(arrowPickup(helper, empty) == AbstractArrow.Pickup.CREATIVE_ONLY, "Infinity gene cannot fire without arrows");

        ServerPlayer spectral = archer(helper, "ag_spectral", new BlockPos(1, 2, 5), new ItemStack(Items.SPECTRAL_ARROW, 2));
        genetics(spectral).addGene(Genes.INFINITY);
        shootBow(spectral);
        check(spectral.getInventory().countItem(Items.SPECTRAL_ARROW) == 1, "Infinity gene made spectral arrows infinite");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void geneArgumentParsesCommandNames(GameTestHelper helper) throws CommandSyntaxException {
        StringReader reader = new StringReader("climb_walls @s");
        check(GeneArgumentType.gene().parse(reader) == Genes.CLIMB_WALLS, "climb_walls did not parse");
        check(reader.getCursor() == "climb_walls".length(), "Parser consumed past the gene name");
        try {
            GeneArgumentType.gene().parse(new StringReader("not_a_gene"));
            helper.fail("An unknown gene name parsed");
        } catch (CommandSyntaxException expected) {
            helper.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void geneCommandsEditTheTargetPlayer(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ag_command");
        CommandSourceStack source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        Commands commands = helper.getLevel().getServer().getCommands();

        commands.performPrefixedCommand(source, "gene add @s flight");
        commands.performPrefixedCommand(source, "gene add @s speed");
        check(genetics(player).hasGene(Genes.FLIGHT) && genetics(player).hasGene(Genes.SPEED), "/gene add failed: " + genetics(player).getGenes());
        commands.performPrefixedCommand(source, "gene remove @s flight");
        check(!genetics(player).hasGene(Genes.FLIGHT) && genetics(player).hasGene(Genes.SPEED), "/gene remove failed: " + genetics(player).getGenes());
        commands.performPrefixedCommand(source, "gene clear @s");
        check(genetics(player).geneCount() == 0, "/gene clear failed: " + genetics(player).getGenes());

        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(0).withSuppressedOutput(), "gene add @s flight");
        check(!genetics(player).hasGene(Genes.FLIGHT), "/gene add ran without permission");
        helper.succeed();
    }

    /** Hurts the player through vanilla and reports whether a gene negated the damage. */
    private static boolean negated(ServerPlayer player, DamageSource source, float amount) {
        player.invulnerableTime = 0;
        return !player.hurt(source, amount);
    }

    private static void tickSeconds(ServerPlayer player, int seconds) {
        for (int i = 0; i < seconds * 20; i++) {
            player.doTick();
        }
    }

    private static void hit(Projectile projectile, HitResult result) {
        try {
            Method onHit = Projectile.class.getDeclaredMethod("onHit", HitResult.class);
            onHit.setAccessible(true);
            onHit.invoke(projectile, result);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InteractionResult useBlock(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static InteractionResult useItem(ServerPlayer player) {
        return player.gameMode.useItem(player, player.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    private static InteractionResult interact(ServerPlayer player, Entity target) {
        return player.interactOn(target, InteractionHand.MAIN_HAND);
    }

    private static List<FireballEntity> fireballs(GameTestHelper helper, ServerPlayer owner) {
        return helper.getLevel().getEntitiesOfClass(FireballEntity.class, owner.getBoundingBox().inflate(4.0), fireball -> fireball.getOwner() == owner);
    }

    private static ServerPlayer archer(GameTestHelper helper, String name, BlockPos pos, ItemStack ammo) {
        ServerPlayer archer = player(helper, name, pos);
        archer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        if (!ammo.isEmpty()) archer.getInventory().add(ammo);
        return archer;
    }

    /** Releases a fully drawn bow. */
    private static void shootBow(ServerPlayer archer) {
        ItemStack bow = archer.getMainHandItem();
        bow.releaseUsing(archer.getLevel(), archer, bow.getUseDuration() - 20);
    }

    private static AbstractArrow.Pickup arrowPickup(GameTestHelper helper, ServerPlayer owner) {
        List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                owner.getBoundingBox().inflate(4.0), arrow -> arrow.getOwner() == owner);
        check(arrows.size() == 1, owner.getGameProfile().getName() + " shot " + arrows.size() + " arrows");
        return arrows.get(0).pickup;
    }

    private static boolean hasFleeGoal(Mob mob) {
        return ((MobAccessor) mob).advancedgenetics$getGoalSelector().getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof FleePlayerGoal);
    }
}
