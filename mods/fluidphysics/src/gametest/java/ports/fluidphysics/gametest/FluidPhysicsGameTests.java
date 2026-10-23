/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Dedicated-server tests for the library, using the development-only test fluids.
 */
public final class FluidPhysicsGameTests implements FabricGameTest {

    private static final String EMPTY = "fluidphysics:gametest/empty16";
    private static final int TIMEOUT = 200;

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void swimmableFluidDrowns(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 3, 1, 3);

        final LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));

        zombie.setAirSupply(100);

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> check(zombie.getAirSupply() < 100,
                        "an entity in a swimmable fluid kept its air: " + zombie.getAirSupply()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void nonSwimmableFluidDoesNotDrown(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 4, 1, 3, 1, 3);

        final LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));

        zombie.setAirSupply(100);

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> check(zombie.getAirSupply() >= 100,
                        "an entity in a non-drowning fluid lost air: " + zombie.getAirSupply()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void swimmableFluidSetsTheSwimmingPose(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 3, 1, 3);

        final LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));

        zombie.setSprinting(true);
        zombie.setSwimming(false);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> check(zombie.isSwimming(), "a sprinting entity in a swimmable fluid is not swimming"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void nonSwimmableFluidDoesNotSetTheSwimmingPose(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 4, 1, 3, 1, 3);

        final LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));

        zombie.setSprinting(true);
        zombie.setSwimming(false);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> check(!zombie.isSwimming(),
                        "a sprinting entity in a non-swimmable fluid is swimming"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void swimmableFluidSupportsBoats(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 2, 1, 5, 1, 5);
        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 2, 6, 10, 1, 5);

        final Boat onFluid = helper.spawn(EntityType.BOAT, new BlockPos(3, 4, 3));
        final Boat onPlain = helper.spawn(EntityType.BOAT, new BlockPos(8, 4, 3));

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {

                    check(onFluid.getY() > onPlain.getY() + 1.0D, "the boat did not float on the swimmable fluid: "
                            + onFluid.getY() + " vs " + onPlain.getY() + " in the plain fluid");
                    check(onPlain.getY() < helper.absolutePos(new BlockPos(0, 2, 0)).getY() + 0.5D,
                            "the boat did not sink through the plain fluid: " + onPlain.getY());
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void fluidPathTypesMatchTheirProperties(final GameTestHelper helper) {

        helper.setBlock(new BlockPos(2, 1, 2), FluidPhysicsTestFluids.SWIMMABLE_BLOCK);
        helper.setBlock(new BlockPos(6, 1, 2), FluidPhysicsTestFluids.PLAIN_BLOCK);

        final BlockPathTypes swimmable = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(),
                mutablePos(helper, new BlockPos(2, 1, 2)));
        final BlockPathTypes plain = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(),
                mutablePos(helper, new BlockPos(6, 1, 2)));

        check(BlockPathTypes.WATER == swimmable, "the swimmable fluid has path type " + swimmable);
        check(BlockPathTypes.WALKABLE == plain, "the plain fluid has path type " + plain);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void swimmableFluidPushesEntities(final GameTestHelper helper) {

        flowingChannel(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK);

        final LivingEntity[] zombie = new LivingEntity[1];
        final double[] startX = new double[1];

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {

                    check(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(5, 1, 2))).isEmpty(),
                            "the test fluid did not flow down the channel");

                    zombie[0] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 1, 2));
                    startX[0] = zombie[0].getX();
                })
                .thenIdle(20)
                .thenExecute(() -> check(zombie[0].getX() > startX[0] + 0.05D,
                        "a swimmable fluid's flow did not push the entity: " + startX[0] + " -> " + zombie[0].getX()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void nonSwimmableFluidDoesNotPushEntities(final GameTestHelper helper) {

        flowingChannel(helper, FluidPhysicsTestFluids.PLAIN_BLOCK);

        final LivingEntity[] zombie = new LivingEntity[1];
        final double[] startX = new double[1];

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {

                    check(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(5, 1, 2))).isEmpty(),
                            "the test fluid did not flow down the channel");

                    zombie[0] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 1, 2));
                    startX[0] = zombie[0].getX();
                })
                .thenIdle(20)
                .thenExecute(() -> check(Math.abs(zombie[0].getX() - startX[0]) < 0.05D,
                        "a non-pushing fluid moved the entity: " + startX[0] + " -> " + zombie[0].getX()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void fluidFallDistanceModifiers(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 3, 1, 3);
        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 4, 6, 8, 1, 3);

        final LivingEntity swimmable = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        final LivingEntity plain = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(7, 2, 2));

        swimmable.setNoGravity(true);
        plain.setNoGravity(true);

        helper.startSequence()
                .thenExecute(() -> {
                    swimmable.fallDistance = 10.0F;
                    plain.fallDistance = 10.0F;
                })
                .thenIdle(1)
                .thenExecute(() -> {

                    check(Math.abs(swimmable.fallDistance - 4.0F) < 0.01F,
                            "the swimmable fluid's fall distance modifier gave " + swimmable.fallDistance);
                    check(Math.abs(plain.fallDistance - 1.0F) < 0.01F,
                            "the plain fluid's fall distance modifier gave " + plain.fallDistance);
                })
                .thenSucceed();
    }

    private static void floodPool(final GameTestHelper helper, final Block fluid, final int fromY, final int toY,
                                  final int fromX, final int toX, final int fromZ, final int toZ) {

        for (int x = fromX - 1; x <= toX + 1; ++x) {
            for (int z = fromZ - 1; z <= toZ + 1; ++z) {
                helper.setBlock(new BlockPos(x, fromY - 1, z), Blocks.STONE);
            }
        }

        for (int y = fromY; y <= toY; ++y) {
            for (int x = fromX; x <= toX; ++x) {
                for (int z = fromZ; z <= toZ; ++z) {
                    final boolean edge = x == fromX || x == toX || z == fromZ || z == toZ;

                    helper.setBlock(new BlockPos(x, y, z), edge ? Blocks.STONE : fluid);
                }
            }
        }
    }

    private static void flowingChannel(final GameTestHelper helper, final Block fluid) {

        for (int x = 0; x <= 9; ++x) {
            for (int z = 0; z <= 4; ++z) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }

            for (int y = 1; y <= 2; ++y) {
                helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
        }

        for (int z = 1; z <= 3; ++z) {
            for (int y = 1; y <= 2; ++y) {
                helper.setBlock(new BlockPos(0, y, z), Blocks.STONE);
            }
        }

        helper.setBlock(new BlockPos(1, 1, 2), fluid);
    }

    private static BlockPos.MutableBlockPos mutablePos(final GameTestHelper helper, final BlockPos relative) {

        final BlockPos absolute = helper.absolutePos(relative);

        return new BlockPos.MutableBlockPos(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    private static void check(final boolean condition, final String message) {

        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
