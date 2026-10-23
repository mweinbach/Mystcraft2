/*
 * Mekanism 1.19.2 port - development-only Fabric GameTests.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.List;
import mekanism.common.registries.MekanismFluids;
import mekanism.generators.common.registries.GeneratorsFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import ports.fluidphysics.FluidPhysics;
import ports.fluidphysics.FluidProperties;
import ports.mekanism.fabric.FabricFluidPhysics;

/**
 * Fabric only: Mekanism's fluids are registered with the Fluid Physics library from their {@code FluidType}, and a
 * fluid whose type turns the physics off gets none of them. Forge applies {@code FluidType} itself, so these checks
 * have no Forge counterpart; the shared behavior is in {@link FluidEntityGameTests}.
 */
public class FabricFluidPhysicsGameTests {

    @GameTest(template = TEMPLATE)
    public static void everyModuleFluidIsRegisteredFromItsType(GameTestHelper helper) {
        int count = 0;
        for (var fluids : List.of(MekanismFluids.FLUIDS, GeneratorsFluids.FLUIDS)) {
            for (var fluid : fluids.getAllFluids()) {
                FluidProperties expected = FabricFluidPhysics.properties(fluid.getFluidType());
                for (Fluid instance : List.<Fluid>of(fluid.getStillFluid(), fluid.getFlowingFluid())) {
                    FluidProperties actual = FluidPhysics.propertiesOf(instance);
                    check(actual != null, instance + " has no fluid physics");
                    check(actual.canSwim() == expected.canSwim() && actual.canDrown() == expected.canDrown()
                                && actual.canPushEntity() == expected.canPushEntity() && actual.supportsBoating() == expected.supportsBoating()
                                && actual.motionScale() == expected.motionScale() && actual.fallDistanceModifier() == expected.fallDistanceModifier()
                                && actual.pathType() == expected.pathType() && actual.canExtinguish() == expected.canExtinguish()
                                && actual.adjacentPathType() == expected.adjacentPathType(), instance + " was registered with other properties than its type");
                    // Forge's FluidType.Properties defaults, which no Mekanism fluid overrides.
                    check(actual.canSwim() && actual.canDrown() && actual.canPushEntity() && !actual.supportsBoating()
                                && actual.motionScale() == 0.014D && actual.fallDistanceModifier() == 0.5F && actual.pathType() == BlockPathTypes.WATER
                                && !actual.canExtinguish() && actual.adjacentPathType() == BlockPathTypes.WATER_BORDER,
                          instance + " does not have Forge's default fluid type properties");
                }
                count++;
            }
        }
        check(count > 0, "no Mekanism fluids were found");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void inertFluidDoesNotDrown(GameTestHelper helper) {
        FluidEntityGameTests.floodPool(helper, FabricTestFluids.INERT_BLOCK);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 1, 2));
        cow.setAirSupply(100);
        helper.startSequence()
              .thenIdle(20)
              .thenExecute(() -> check(cow.getAirSupply() >= 100, "a cow in a non-drowning fluid lost air: " + cow.getAirSupply()))
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void inertFluidDoesNotSetSwimPose(GameTestHelper helper) {
        FluidEntityGameTests.floodPool(helper, FabricTestFluids.INERT_BLOCK);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 1, 2));
        cow.setSprinting(true);
        cow.setSwimming(false);
        helper.startSequence()
              .thenIdle(2)
              .thenExecute(() -> check(!cow.isSwimming(), "a sprinting cow in a non-swimmable fluid is swimming"))
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void inertFluidDoesNotPushEntities(GameTestHelper helper) {
        FluidEntityGameTests.flowingChannel(helper, FabricTestFluids.INERT_BLOCK);
        LivingEntity[] zombie = new LivingEntity[1];
        double[] startX = new double[1];
        helper.startSequence()
              .thenIdle(40)
              .thenExecute(() -> {
                  check(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(4, 1, 2))).isEmpty(), "the inert fluid did not flow down the channel");
                  zombie[0] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 2));
                  startX[0] = zombie[0].getX();
              })
              .thenIdle(20)
              .thenExecute(() -> check(Math.abs(zombie[0].getX() - startX[0]) < 0.05D,
                    "a non-pushing fluid moved the entity: " + startX[0] + " -> " + zombie[0].getX()))
              .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void inertFluidPathsAsItsTypeSays(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, FabricTestFluids.INERT_BLOCK);
        BlockPathTypes type = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(), helper.absolutePos(pos).mutable());
        check(type == BlockPathTypes.WALKABLE, "the inert fluid has path type " + type);
        helper.succeed();
    }
}
