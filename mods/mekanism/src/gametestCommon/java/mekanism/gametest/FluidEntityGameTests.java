/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.common.registries.MekanismFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.fluids.FluidType;

/**
 * Entities in Mekanism's fluids behave as their fluid type says. Every Mekanism fluid keeps Forge's
 * {@code FluidType.Properties} defaults: swimmable, drowning, pushing, a fall distance modifier of 0.5 and the water
 * path type. Forge applies them itself; Fabric applies them through the bundled Fluid Physics library.
 */
public class FluidEntityGameTests {

    private static Block brine() {
        return MekanismFluids.BRINE.getBlock();
    }

    private static FluidType brineType() {
        return MekanismFluids.BRINE.getFluidType();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void submergedEntityDrownsInBrine(GameTestHelper helper) {
        floodPool(helper, brine());
        // Cows have none of the undead or water-mob exceptions that let vanilla mobs breathe in water.
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 1, 2));
        cow.setAirSupply(100);
        helper.startSequence()
              .thenIdle(20)
              .thenExecute(() -> check(cow.getAirSupply() < 100, "a cow under brine kept its air: " + cow.getAirSupply()))
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void sprintingEntitySwimsInBrine(GameTestHelper helper) {
        floodPool(helper, brine());
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 1, 2));
        cow.setSprinting(true);
        cow.setSwimming(false);
        helper.startSequence()
              .thenIdle(2)
              .thenExecute(() -> check(cow.isSwimming(), "a sprinting cow under brine is not swimming"))
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void entityTracksBrineFluidType(GameTestHelper helper) {
        floodPool(helper, brine());
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 1, 2));
        helper.startSequence()
              .thenIdle(2)
              .thenExecute(() -> {
                  check(cow.getMaxHeightFluidType() == brineType(), "the cow's tallest fluid type is " + cow.getMaxHeightFluidType());
                  check(cow.isInFluidType(brineType()), "the cow is not in brine's fluid type");
                  check(cow.isEyeInFluidType(brineType()), "the cow's eyes are not in brine's fluid type");
              })
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void flowingBrinePushesEntities(GameTestHelper helper) {
        flowingChannel(helper, brine());
        LivingEntity[] zombie = new LivingEntity[1];
        double[] startX = new double[1];
        helper.startSequence()
              .thenIdle(40)
              .thenExecute(() -> {
                  check(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(4, 1, 2))).isEmpty(), "brine did not flow down the channel");
                  zombie[0] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 2));
                  startX[0] = zombie[0].getX();
              })
              .thenIdle(20)
              .thenExecute(() -> check(zombie[0].getX() > startX[0] + 0.05D,
                    "flowing brine did not push the entity: " + startX[0] + " -> " + zombie[0].getX()))
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void brineScalesFallDistance(GameTestHelper helper) {
        floodPool(helper, brine());
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setNoGravity(true);
        helper.startSequence()
              .thenExecute(() -> cow.fallDistance = 10.0F)
              .thenIdle(1)
              .thenExecute(() -> check(Math.abs(cow.fallDistance - 5.0F) < 0.01F, "brine's fall distance modifier gave " + cow.fallDistance))
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void brinePathsAsWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, brine());
        BlockPathTypes type = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(), helper.absolutePos(pos).mutable());
        check(type == BlockPathTypes.WATER, "brine has path type " + type);
        helper.succeed();
    }

    /**
     * A 3x3 stone-walled pool, four blocks deep, whose single inner column at (2, 1..4, 2) holds the fluid.
     */
    static void floodPool(GameTestHelper helper, Block fluid) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 2 ? fluid : Blocks.STONE);
                }
            }
        }
    }

    /**
     * A closed channel along +x, three blocks wide, with one source at its west end. The fluid flows east to x = 6.
     */
    static void flowingChannel(GameTestHelper helper, Block fluid) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean wall = x == 0 || x == 7 || z == 0 || z == 4;
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        helper.setBlock(new BlockPos(1, 1, 2), fluid);
    }
}
