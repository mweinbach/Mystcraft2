/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.math.FloatingLong;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.CommonWorldTickHandler;
import mekanism.common.Mekanism;
import mekanism.common.base.KeySync;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.inventory.BinMekanismInventory;
import mekanism.common.item.block.ItemBlockBin;
import mekanism.common.item.gear.ItemScubaTank;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Server player and living events reach the same Mekanism listeners Forge calls.
 */
public class PlayerEventGameTests {

    private static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void freeRunnersPreventFallDamage(GameTestHelper helper) {
        ServerPlayer runner = survival(helper, new BlockPos(1, 2, 1));
        ItemStack boots = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.FREE_RUNNERS.get()), MekanismConfig.gear.freeRunnerMaxEnergy);
        runner.setItemSlot(EquipmentSlot.FEET, boots);
        runner.setHealth(20);
        runner.invulnerableTime = 0;
        runner.causeFallDamage(10, 1, DamageSource.FALL);
        check(runner.getHealth() == 20, "Free runners left the player at " + state(runner));

        ServerPlayer bare = survival(helper, new BlockPos(3, 2, 1));
        bare.setHealth(20);
        bare.invulnerableTime = 0;
        bare.causeFallDamage(10, 1, DamageSource.FALL);
        check(bare.getHealth() == 13, "A bare fall of ten blocks dealt unexpected damage, " + state(bare));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void freeRunnersAbsorbPartOfTheFall(GameTestHelper helper) {
        ServerPlayer runner = survival(helper, new BlockPos(1, 2, 1));
        ItemStack boots = new ItemStack(MekanismItems.FREE_RUNNERS.get());
        IEnergyContainer energy = StorageUtils.getEnergyContainer(boots, 0);
        check(energy != null, "Free runners have no energy container");
        energy.insert(FloatingLong.create(5), Action.EXECUTE, AutomationType.MANUAL);
        runner.setItemSlot(EquipmentSlot.FEET, boots);
        runner.setHealth(20);
        runner.invulnerableTime = 0;
        runner.hurt(DamageSource.FALL, 8);
        check(runner.getHealth() == 17, "Partial free-runner absorb left " + state(runner));
        check(energy.getEnergy().isZero(), "Partial absorb left energy in the boots");

        ServerPlayer bare = survival(helper, new BlockPos(3, 2, 1));
        bare.setHealth(20);
        bare.invulnerableTime = 0;
        bare.hurt(DamageSource.FALL, 8);
        check(bare.getHealth() == 12, "Bare fall damage of eight left " + state(bare));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void jetpackRaisesThePlayer(GameTestHelper helper) {
        ServerPlayer pilot = survival(helper, new BlockPos(1, 2, 1));
        ItemStack jetpack = ChemicalUtil.getFilledVariant(new ItemStack(MekanismItems.JETPACK.get()), MekanismConfig.gear.jetpackMaxGas, MekanismGases.HYDROGEN);
        pilot.setItemSlot(EquipmentSlot.CHEST, jetpack);
        Mekanism.keyMap.add(pilot.getUUID(), KeySync.ASCEND);
        pilot.setOnGround(false);
        pilot.setDeltaMovement(0, 0, 0);
        pilot.doTick();
        check(pilot.getDeltaMovement().y > 0, "Jetpack thrust was " + pilot.getDeltaMovement().y);

        ServerPlayer grounded = survival(helper, new BlockPos(3, 2, 1));
        grounded.setOnGround(false);
        grounded.setDeltaMovement(0, 0, 0);
        grounded.doTick();
        check(grounded.getDeltaMovement().y <= 0, "A player without a jetpack rose");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void radiationDamagesOnLivingTick(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        Pig pig = helper.spawn(EntityType.PIG, 2, 2, 2);
        pig.setNoGravity(true);
        radiate(pig);
        float before = pig.getHealth();
        for (int i = 0; i < 40 && pig.getHealth() >= before; i++) {
            pig.tick();
        }
        check(pig.getHealth() < before, "Radiation on a living tick left the pig at " + pig.getHealth());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void radiationDamagesOnPlayerTick(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(2, 2, 2));
        player.setNoGravity(true);
        radiate(player);
        float before = player.getHealth();
        for (int i = 0; i < 40 && player.getHealth() >= before; i++) {
            player.invulnerableTime = 0;
            player.doTick();
        }
        check(player.getHealth() < before, "Radiation on a player tick left " + state(player));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void scubaBlocksMagic(GameTestHelper helper) {
        ServerPlayer diver = survival(helper, new BlockPos(1, 2, 1));
        ItemStack tank = ChemicalUtil.getFilledVariant(new ItemStack(MekanismItems.SCUBA_TANK.get()), MekanismConfig.gear.scubaMaxGas, MekanismGases.OXYGEN);
        ((ItemScubaTank) tank.getItem()).setFlowing(tank, true);
        diver.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MekanismItems.SCUBA_MASK.get()));
        diver.setItemSlot(EquipmentSlot.CHEST, tank);
        diver.setHealth(20);
        diver.invulnerableTime = 0;
        check(!diver.hurt(DamageSource.MAGIC, 6), "Flowing scuba gear let magic damage through");
        check(diver.getHealth() == 20, "Scuba gear left " + state(diver));

        ServerPlayer bare = survival(helper, new BlockPos(3, 2, 1));
        bare.setHealth(20);
        bare.invulnerableTime = 0;
        check(bare.hurt(DamageSource.MAGIC, 6), "Magic damage missed a bare player, " + state(bare));
        check(bare.getHealth() < 20, "A bare player kept full health after magic damage, " + state(bare));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mekaSuitMinesFasterInAir(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 2));

        ServerPlayer plain = survival(helper, new BlockPos(1, 2, 1));
        plain.setOnGround(true);
        float plainGround = plain.getDestroySpeed(stone);
        plain.setOnGround(false);
        float plainAir = plain.getDestroySpeed(stone);
        near(plainAir, plainGround / 5, "Bare hands mined at an unexpected speed in the air");

        ServerPlayer suited = survival(helper, new BlockPos(3, 2, 1));
        ItemStack pants = new ItemStack(MekanismItems.MEKASUIT_PANTS.get());
        ((IModuleContainerItem) pants.getItem()).addModule(pants, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT.get());
        check(MekanismAPI.getModuleHelper().isEnabled(pants, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT), "The gyroscopic module was disabled");
        suited.setItemSlot(EquipmentSlot.LEGS, pants);
        suited.setOnGround(true);
        float ground = suited.getDestroySpeed(stone);
        suited.setOnGround(false);
        float air = suited.getDestroySpeed(stone);
        near(air, ground, "Gyroscopic stabilization did not cancel the air mining penalty");
        float progress = stone.getDestroyProgress(suited, helper.getLevel(), absolute);
        int divisor = suited.hasCorrectToolForDrops(stone) ? 30 : 100;
        near(progress, air / stone.getDestroySpeed(helper.getLevel(), absolute) / divisor, "Destroy progress ignored the break-speed event");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mekaSuitMinesFasterUnderwater(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.WATER);
        ServerPlayer suited = survival(helper, new BlockPos(2, 2, 2));
        ItemStack pants = new ItemStack(MekanismItems.MEKASUIT_PANTS.get());
        ((IModuleContainerItem) pants.getItem()).addModule(pants, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT.get());
        suited.setItemSlot(EquipmentSlot.LEGS, pants);
        suited.setNoGravity(true);
        suited.doTick();
        check(suited.isEyeInFluid(FluidTags.WATER), "The player's eyes were not in water");
        suited.setOnGround(true);
        suited.setDeltaMovement(0, 0, 0);
        float wet = suited.getDestroySpeed(stone);

        ServerPlayer dry = survival(helper, new BlockPos(5, 2, 5));
        ItemStack dryPants = new ItemStack(MekanismItems.MEKASUIT_PANTS.get());
        ((IModuleContainerItem) dryPants.getItem()).addModule(dryPants, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT.get());
        dry.setItemSlot(EquipmentSlot.LEGS, dryPants);
        dry.setOnGround(true);
        float drySpeed = dry.getDestroySpeed(stone);
        near(wet, drySpeed, "Underwater gyroscopic stabilization did not cancel the mining penalty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void binInsertConsumesTheGrid(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        BlockPos table = new BlockPos(1, 1, 1);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        menu.getSlot(1).set(new ItemStack(MekanismBlocks.BASIC_BIN.asItem()));
        menu.getSlot(2).set(new ItemStack(Items.COBBLESTONE, 64));
        ItemStack result = menu.getSlot(0).getItem();
        check(result.getItem() instanceof ItemBlockBin, "The bin insert recipe did not produce a bin");
        ItemStack taken = menu.getSlot(0).remove(result.getCount());
        menu.getSlot(0).onTake(player, taken);
        BinMekanismInventory inventory = BinMekanismInventory.create(taken);
        check(inventory != null, "The crafted bin has no inventory");
        check(inventory.getBinSlot().getCount() == 64, "The crafted bin stored " + inventory.getBinSlot().getCount());
        check(menu.getSlot(2).getItem().isEmpty(), "Cobblestone remained in the crafting grid");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void cardboardBoxCancelsItemSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        ItemEntity dropped = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.COBBLESTONE));
        CommonWorldTickHandler.monitoringCardboardBox = true;
        try {
            check(!level.addFreshEntity(dropped), "A cardboard box allowed an item to spawn");
            check(dropped.isRemoved(), "The cancelled item was kept");
        } finally {
            CommonWorldTickHandler.monitoringCardboardBox = false;
        }
        ItemEntity allowed = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIRT));
        check(level.addFreshEntity(allowed), "Items stopped spawning after the cardboard box");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void cancelledJoinIsNotAdded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 2));
        ItemEntity dropped = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.COBBLESTONE));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, event -> {
            if (event.getEntity() == dropped) {
                event.setCanceled(true);
            }
        });
        check(!level.addFreshEntity(dropped), "Cancelling entity join still added the item");
        check(level.getEntity(dropped.getId()) == null, "The cancelled item is in the level");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void entityLeaveFiresWhenDiscarded(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, 3, 2, 3);
        AtomicBoolean left = new AtomicBoolean();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityLeaveLevelEvent.class, event -> {
            if (event.getEntity() == pig && event.getLevel() == helper.getLevel()) {
                left.set(true);
            }
        });
        pig.discard();
        check(left.get(), "Discarding an entity did not fire the leave event");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void jumpAndDeathEventsFire(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        AtomicBoolean jumped = new AtomicBoolean();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingJumpEvent.class, event -> {
            if (event.getEntity() == player) {
                jumped.set(true);
            }
        });
        player.jumpFromGround();
        check(jumped.get(), "Jumping did not fire the living jump event");

        Pig pig = helper.spawn(EntityType.PIG, 3, 2, 3);
        pig.setNoGravity(true);
        AtomicInteger deaths = new AtomicInteger();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, LivingDeathEvent.class, event -> {
            if (event.getEntity() == pig) {
                deaths.incrementAndGet();
            }
        });
        pig.hurt(DamageSource.GENERIC, 40);
        check(!pig.isAlive(), "The pig survived a lethal hit");
        check(deaths.get() == 1, "Living death fired " + deaths.get() + " times");
        helper.succeed();
    }

    private static void radiate(LivingEntity entity) {
        var capability = entity.getCapability(Capabilities.RADIATION_ENTITY);
        check(capability.isPresent(), "The entity has no radiation capability");
        IRadiationEntity radiation = capability.orElseThrow(IllegalStateException::new);
        radiation.set(1_000);
        check(radiation.getRadiation() >= 100, "The radiation dose was not stored");
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "player-event"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        Abilities abilities = player.getAbilities();
        abilities.instabuild = false;
        abilities.invulnerable = false;
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();
        clearSpawnProtection(player);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }

    private static void clearSpawnProtection(ServerPlayer player) {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not clear spawn protection: " + e.getMessage());
        }
    }

    private static String state(ServerPlayer player) {
        return "health " + player.getHealth()
              + ", invulnerable " + player.getAbilities().invulnerable
              + ", mayfly " + player.getAbilities().mayfly
              + ", creative " + player.isCreative()
              + ", spawnProtection " + spawnProtection(player);
    }

    private static int spawnProtection(ServerPlayer player) {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            return field.getInt(player);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private static void near(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > 0.001F) {
            throw new GameTestAssertException(message + " (expected " + expected + ", was " + actual + ")");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
