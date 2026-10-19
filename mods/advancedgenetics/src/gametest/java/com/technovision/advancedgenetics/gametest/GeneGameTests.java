package com.technovision.advancedgenetics.gametest;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.commands.GeneArgumentType;
import com.technovision.advancedgenetics.common.entity.FireballEntity;
import com.technovision.advancedgenetics.common.goal.FleePlayerGoal;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.genetics;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.player;

@GameTestHolder(AdvancedGenetics.MOD_ID)
@PrefixGameTestTemplate(false)
public class GeneGameTests {

    @GameTest(template = TEMPLATE)
    public static void playerCapabilityPersistsGenesAndSkipsUnknownNames(GameTestHelper helper) {
        FakePlayer player = player(helper, "ag_persist", Genes.FLIGHT, Genes.SPEED);
        check(player.getCapability(ComponentRegistry.PLAYER_GENETICS).isPresent(), "Genetics capability is not attached");

        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        CompoundTag capability = saved.getCompound("ForgeCaps").getCompound(ComponentRegistry.PLAYER_GENETICS_ID.toString());
        capability.getCompound("genes").putString("REMOVED_GENE", "Removed Gene");

        FakePlayer restored = player(helper, "ag_restore");
        restored.load(saved);
        PlayerGeneticsComponent genes = genetics(restored);
        check(genes.geneCount() == 2, "Expected 2 restored genes, got " + genes.getGenes());
        check(genes.hasGene(Genes.FLIGHT) && genes.hasGene(Genes.SPEED), "Restored genes differ: " + genes.getGenes());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void respawnCopiesGenesAndKeepInventoryKeepsItems(GameTestHelper helper) {
        FakePlayer keeper = player(helper, "ag_keeper", Genes.KEEP_INVENTORY, Genes.FLIGHT);
        keeper.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        FakePlayer keeperClone = player(helper, "ag_keeper2");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(keeperClone, keeper, true));
        check(genetics(keeperClone).hasGene(Genes.KEEP_INVENTORY) && genetics(keeperClone).hasGene(Genes.FLIGHT),
                "Respawned player lost genes: " + genetics(keeperClone).getGenes());
        check(keeperClone.getInventory().countItem(Items.DIAMOND) == 3, "Keep Inventory did not restore the inventory");

        FakePlayer plain = player(helper, "ag_plain", Genes.SPEED);
        plain.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        FakePlayer plainClone = player(helper, "ag_plain2");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(plainClone, plain, true));
        check(genetics(plainClone).hasGene(Genes.SPEED), "Respawned player lost Speed");
        check(plainClone.getInventory().isEmpty(), "Inventory was restored without Keep Inventory");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void keepInventoryGeneCancelsDeathDrops(GameTestHelper helper) throws ReflectiveOperationException {
        Method dropEquipment = ObfuscationReflectionHelper.findMethod(Player.class, "m_5907_");
        BlockPos dropPos = new BlockPos(1, 2, 1);

        FakePlayer keeper = player(helper, "ag_nodrop", Genes.KEEP_INVENTORY);
        keeper.getInventory().add(new ItemStack(Items.EMERALD, 2));
        dropEquipment.invoke(keeper);
        check(keeper.getInventory().countItem(Items.EMERALD) == 2, "Keep Inventory player dropped items");
        helper.assertItemEntityNotPresent(Items.EMERALD, dropPos, 3.0);

        FakePlayer plain = player(helper, "ag_drop");
        plain.getInventory().add(new ItemStack(Items.EMERALD, 2));
        dropEquipment.invoke(plain);
        check(plain.getInventory().isEmpty(), "Player without Keep Inventory kept items");
        helper.assertItemEntityPresent(Items.EMERALD, dropPos, 3.0);
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void webWalkingGeneSkipsCobwebSlowdown(GameTestHelper helper) throws IllegalAccessException {
        Field stuckSpeed = ObfuscationReflectionHelper.findField(Entity.class, "f_19865_");
        Vec3 cobweb = new Vec3(0.25, 0.05, 0.25);

        FakePlayer plain = player(helper, "ag_stuck");
        plain.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), cobweb);
        check(cobweb.equals(stuckSpeed.get(plain)), "Cobweb did not slow a normal player");

        FakePlayer walker = player(helper, "ag_web", Genes.WEB_WALKING);
        walker.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), cobweb);
        check(Vec3.ZERO.equals(stuckSpeed.get(walker)), "Web Walking player was slowed by a cobweb");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void climbWallsGeneClimbsSolidWalls(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 2, 2);
        helper.setBlock(feet.below(), Blocks.STONE);
        helper.setBlock(feet.north(), Blocks.STONE);
        helper.setBlock(feet.north().above(), Blocks.STONE);

        FakePlayer player = player(helper, "ag_climb", feet);
        check(!player.onClimbable(), "A stone wall is climbable without the gene");
        genetics(player).addGene(Genes.CLIMB_WALLS);
        check(player.onClimbable(), "Climb Walls player cannot climb a stone wall");

        FakePlayer open = player(helper, "ag_open", new BlockPos(5, 2, 5));
        genetics(open).addGene(Genes.CLIMB_WALLS);
        check(!open.onClimbable(), "Climb Walls player climbs without a wall");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void damageGenesCancelMatchingAttacks(GameTestHelper helper) {
        FakePlayer plain = player(helper, "ag_hurt");
        check(!attack(plain, DamageSource.FALL, 4.0F), "Fall damage was cancelled without a gene");

        FakePlayer feather = player(helper, "ag_fall", Genes.NO_FALL_DAMAGE);
        check(attack(feather, DamageSource.FALL, 4.0F), "No Fall Damage did not cancel fall damage");
        check(!attack(feather, DamageSource.GENERIC, 4.0F), "No Fall Damage cancelled generic damage");

        FakePlayer immune = player(helper, "ag_poison", Genes.POISON_IMMUNITY);
        immune.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        check(attack(immune, DamageSource.MAGIC, 1.0F), "Poison Immunity did not cancel poison damage");
        check(!immune.hasEffect(MobEffects.POISON), "Poison Immunity did not clear poison");

        FakePlayer dragon = player(helper, "ag_dragon", Genes.DRAGONS_HEALTH);
        ItemStack crystal = new ItemStack(ItemRegistry.DRAGON_HEALTH_CRYSTAL.get());
        dragon.getInventory().setItem(0, crystal);
        check(attack(dragon, DamageSource.GENERIC, 5.0F), "Dragon's Health crystal did not absorb damage");
        check(crystal.getDamageValue() == 5, "Crystal durability loss was " + crystal.getDamageValue());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serverTicksApplyFlightEffectsAndHunger(GameTestHelper helper) {
        FakePlayer player = player(helper, "ag_tick", Genes.FLIGHT, Genes.RESISTANCE, Genes.NO_HUNGER);
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
        FakePlayer spectator = player(helper, "ag_spectator");
        check(spectator.setGameMode(GameType.SPECTATOR), "Could not switch to spectator mode");
        check(spectator.getAbilities().mayfly && spectator.getAbilities().flying, "Spectator mode did not enable flight");
        tickSeconds(spectator, 4);
        check(spectator.getAbilities().mayfly && spectator.getAbilities().flying, "Spectator lost flight without the Flight gene");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireballExplosionCreditsTheShooter(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos target = new BlockPos(4, 2, 4);
        helper.setBlock(target.below(), Blocks.STONE);
        LivingEntity chicken = helper.spawn(EntityType.CHICKEN, target);
        FakePlayer shooter = player(helper, "ag_shooter");
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
    public static void fireballDamagesItsDirectTargetOnce(GameTestHelper helper) throws ReflectiveOperationException {
        LivingEntity golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 4));
        FakePlayer shooter = player(helper, "ag_burner");
        FireballEntity fireball = new FireballEntity(helper.getLevel(), shooter, new Vec3(0, -1, 0));
        fireball.moveTo(golem.getEyePosition());

        AtomicInteger fireballHits = new AtomicInteger();
        Consumer<LivingAttackEvent> counter = event -> {
            if (event.getEntity() == golem && event.getSource().getMsgId().equals("fireball")
                    && event.getSource().getEntity() == shooter) {
                fireballHits.incrementAndGet();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, counter);
        try {
            hit(fireball, new EntityHitResult(golem));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        check(fireballHits.get() == 1, "Shooter's fireball damaged its target " + fireballHits.get() + " times");
        check(golem.isOnFire(), "Fireball did not ignite its target");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void eatGrassGeneTurnsGrassToDirt(GameTestHelper helper) {
        BlockPos grass = new BlockPos(1, 1, 1);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);

        FakePlayer plain = player(helper, "ag_nograss");
        plain.getFoodData().setFoodLevel(10);
        check(!rightClick(helper, plain, grass), "Grass click was cancelled without the gene");
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, grass);

        FakePlayer grazer = player(helper, "ag_grass", Genes.EAT_GRASS);
        grazer.getFoodData().setFoodLevel(10);
        check(rightClick(helper, grazer, grass), "Eat Grass did not handle the click");
        helper.assertBlockPresent(Blocks.DIRT, grass);
        check(grazer.getFoodData().getFoodLevel() == 11, "Eat Grass fed to " + grazer.getFoodData().getFoodLevel());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void milkyGeneFillsOneBucketFromAStack(GameTestHelper helper) {
        FakePlayer cow = player(helper, "ag_cow", Genes.MILKY);
        FakePlayer milker = player(helper, "ag_milker");
        milker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));

        check(interact(milker, cow), "Milky interaction was not handled");
        check(milker.getMainHandItem().is(Items.BUCKET) && milker.getMainHandItem().getCount() == 2,
                "Milking consumed the wrong bucket count: " + milker.getMainHandItem());
        check(milker.getInventory().countItem(Items.MILK_BUCKET) == 1, "Milk bucket was not added to the inventory");

        check(interact(milker, cow), "Milky cooldown interaction was not handled");
        check(milker.getMainHandItem().getCount() == 2, "Milky ignored its cooldown");

        milker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        check(!interact(milker, cow), "An unrelated interaction was cancelled");
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
    public static void infinityGeneMakesPlainArrowsInfinite(GameTestHelper helper) {
        ArrowItem arrow = (ArrowItem) Items.ARROW;
        ItemStack arrows = new ItemStack(Items.ARROW);
        ItemStack spectral = new ItemStack(Items.SPECTRAL_ARROW);
        ItemStack bow = new ItemStack(Items.BOW);

        FakePlayer plain = player(helper, "ag_archer");
        check(!arrow.isInfinite(arrows, bow, plain), "Arrows are infinite without the gene");

        FakePlayer infinite = player(helper, "ag_infinity", Genes.INFINITY);
        check(arrow.isInfinite(arrows, bow, infinite), "Infinity gene did not make arrows infinite");
        check(!((ArrowItem) Items.SPECTRAL_ARROW).isInfinite(spectral, bow, infinite), "Spectral arrows became infinite");
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
        FakePlayer player = player(helper, "ag_command");
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

    private static boolean attack(FakePlayer player, DamageSource source, float amount) {
        return MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(player, source, amount));
    }

    private static void tickSeconds(FakePlayer player, int seconds) {
        for (int i = 0; i < seconds * 20; i++) {
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        }
    }

    private static void hit(Projectile projectile, HitResult result) throws ReflectiveOperationException {
        ObfuscationReflectionHelper.findMethod(Projectile.class, "m_6532_", HitResult.class).invoke(projectile, result);
    }

    private static boolean rightClick(GameTestHelper helper, FakePlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
    }

    private static boolean interact(FakePlayer player, Entity target) {
        return MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
    }

    private static boolean hasFleeGoal(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof FleePlayerGoal);
    }
}
