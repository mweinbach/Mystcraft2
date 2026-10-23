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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
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
    public void shallowSwimmableFluidDoesNotStartSwimming(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 1, 1, 5, 1, 5);

        final LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));

        zombie.setSprinting(true);
        zombie.setSwimming(false);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {

                    check(zombie.getEyeY() > helper.absolutePos(new BlockPos(0, 2, 0)).getY(),
                            "the test entity's eyes are not above the shallow fluid");
                    check(!zombie.isSwimming(),
                            "a sprinting entity with only its feet in a swimmable fluid started swimming");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void submergedSwimmableFluidStartsSwimming(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 5, 1, 5);

        final LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));

        zombie.setSprinting(true);
        zombie.setSwimming(false);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> check(zombie.isSwimming(),
                        "a submerged sprinting entity in a swimmable fluid is not swimming"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void swimmingContinuesWithOnlyTheBodyInSwimmableFluid(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 2, 1, 5, 1, 5);

        final LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));

        zombie.setNoGravity(true);
        zombie.setSprinting(true);

        helper.startSequence()
                .thenExecute(() -> {

                    final BlockPos base = helper.absolutePos(new BlockPos(3, 1, 3));

                    // Eyes above the surface, body still in the fluid.
                    zombie.setPos(base.getX() + 0.5D, base.getY() + 0.45D, base.getZ() + 0.5D);
                    zombie.setSwimming(true);
                })
                .thenIdle(1)
                .thenExecute(() -> {

                    check(zombie.getEyeY() > helper.absolutePos(new BlockPos(0, 3, 0)).getY(),
                            "the test entity's eyes are not above the fluid surface");
                    check(zombie.isSwimming(),
                            "an already-swimming entity stopped when its eyes rose above the swimmable fluid");
                })
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
    public void vanillaWaterStillFloatsBoats(final GameTestHelper helper) {

        floodPool(helper, Blocks.WATER, 1, 2, 1, 5, 1, 5);

        final Boat onWater = helper.spawn(EntityType.BOAT, new BlockPos(3, 4, 3));

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> check(onWater.getY() > helper.absolutePos(new BlockPos(0, 2, 0)).getY() + 0.25D,
                        "a boat did not float on vanilla water: " + onWater.getY()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void vanillaWaterStillDrownsAndSwims(final GameTestHelper helper) {

        floodPool(helper, Blocks.WATER, 1, 4, 1, 5, 1, 5);

        // Cows do not have the undead/water-mob canBreatheUnderwater exceptions, so vanilla water drowns them.
        final LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));

        cow.setAirSupply(100);
        cow.setSprinting(true);
        cow.setSwimming(false);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> check(cow.isSwimming(), "a sprinting entity in vanilla water is not swimming"))
                .thenIdle(18)
                .thenExecute(() -> check(cow.getAirSupply() < 100,
                        "an entity in vanilla water kept its air: " + cow.getAirSupply()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void vanillaWaterStillPushesEntities(final GameTestHelper helper) {

        flowingChannel(helper, Blocks.WATER);

        final LivingEntity[] zombie = new LivingEntity[1];
        final double[] startX = new double[1];

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {

                    check(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(5, 1, 2))).isEmpty(),
                            "vanilla water did not flow down the channel");

                    zombie[0] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 1, 2));
                    startX[0] = zombie[0].getX();
                })
                .thenIdle(20)
                .thenExecute(() -> check(zombie[0].getX() > startX[0] + 0.05D,
                        "vanilla water did not push the entity: " + startX[0] + " -> " + zombie[0].getX()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void vanillaWaterStillPathsAsWater(final GameTestHelper helper) {

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);

        final BlockPathTypes water = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(),
                mutablePos(helper, new BlockPos(2, 1, 2)));

        check(BlockPathTypes.WATER == water, "vanilla water has path type " + water);
        helper.succeed();
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

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void registeredFluidsUseWaterTravel(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 5, 1, 5);
        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 4, 6, 10, 1, 5);

        // Gravity off and off the ground, so only the travel friction changes the horizontal speed.
        final LivingEntity swimmable = floatingZombie(helper, new BlockPos(3, 2, 3));
        final LivingEntity plain = floatingZombie(helper, new BlockPos(8, 2, 3));
        final LivingEntity air = floatingZombie(helper, new BlockPos(13, 2, 3));

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    swimmable.setDeltaMovement(0.3D, 0.0D, 0.0D);
                    plain.setDeltaMovement(0.3D, 0.0D, 0.0D);
                    air.setDeltaMovement(0.3D, 0.0D, 0.0D);
                })
                .thenIdle(1)
                .thenExecute(() -> {

                    final double inSwimmable = swimmable.getDeltaMovement().x;
                    final double inPlain = plain.getDeltaMovement().x;
                    final double inAir = air.getDeltaMovement().x;

                    // Water travel keeps 0.8 of the speed per tick; travel in air keeps 0.91.
                    check(inAir > 0.0D && inSwimmable > 0.0D && inPlain > 0.0D,
                            "an entity stopped: " + inSwimmable + ", " + inPlain + ", " + inAir);
                    check(inSwimmable < inAir - 0.01D,
                            "the swimmable fluid did not slow the entity as water: " + inSwimmable + " vs " + inAir);
                    check(inPlain < inAir - 0.01D,
                            "the plain fluid did not slow the entity as water: " + inPlain + " vs " + inAir);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void registeredFluidsLetJumpingEntitiesSwimUp(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 5, 1, 5);
        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 4, 6, 10, 1, 5);

        final Mob swimmable = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        final Mob plain = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 3));
        final double floor = helper.absolutePos(new BlockPos(0, 1, 0)).getY();

        helper.startSequence()
                .thenExecuteFor(40, () -> {
                    swimmable.getJumpControl().jump();
                    plain.getJumpControl().jump();
                })
                .thenExecute(() -> {

                    // A jump from the ground peaks near 1.25 blocks; swimming up reaches the surface, 3.9 blocks up.
                    check(swimmable.getY() > floor + 2.0D,
                            "a jumping entity did not swim up the swimmable fluid: " + (swimmable.getY() - floor));
                    check(plain.getY() > floor + 2.0D,
                            "a jumping entity did not swim up the plain fluid: " + (plain.getY() - floor));
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void submergedBoatRefusesPassengersOnlyInBoatingFluid(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 5, 1, 5);
        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 4, 6, 10, 1, 5);

        // Both boats start on the pool floor, with their eyes in the fluid.
        final Boat inSwimmable = helper.spawn(EntityType.BOAT, new BlockPos(3, 1, 3));
        final Boat inPlain = helper.spawn(EntityType.BOAT, new BlockPos(8, 1, 3));
        final LivingEntity first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(13, 1, 3));
        final LivingEntity second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(13, 1, 8));

        check(!first.startRiding(inSwimmable), "a boat submerged in a boating fluid accepted a passenger");
        check(second.startRiding(inPlain), "a boat in a fluid that does not support boating refused a passenger");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void floatingBoatCarriesPassengersOnBoatingFluid(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 2, 1, 7, 1, 7);

        helper.setBlock(new BlockPos(12, 0, 12), Blocks.STONE);

        final Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(4, 4, 4));
        final LivingEntity passenger = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(12, 1, 12));
        final double surface = helper.absolutePos(new BlockPos(0, 2, 0)).getY();

        passenger.setAirSupply(100);

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> check(passenger.startRiding(boat),
                        "a boat floating on a boating fluid refused a passenger"))
                .thenIdle(30)
                .thenExecute(() -> {

                    check(passenger.getVehicle() == boat, "the passenger left the boat");
                    check(boat.getY() > surface + 0.25D,
                            "the boat sank with a passenger: " + (boat.getY() - surface));
                    check(passenger.getAirSupply() >= 100,
                            "the passenger of a floating boat lost air: " + passenger.getAirSupply());
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void swimmableFluidPushesItemEntities(final GameTestHelper helper) {

        flowingChannel(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK);

        final ItemEntity[] item = new ItemEntity[1];
        final double[] startX = new double[1];

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {

                    check(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(5, 1, 2))).isEmpty(),
                            "the test fluid did not flow down the channel");

                    item[0] = helper.spawnItem(Items.STONE, 5.5F, 1.05F, 2.5F);
                    startX[0] = item[0].getX();
                })
                .thenIdle(20)
                .thenExecute(() -> check(item[0].getX() > startX[0] + 0.05D,
                        "a swimmable fluid's flow did not push the item: " + startX[0] + " -> " + item[0].getX()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void nonSwimmableFluidDoesNotPushItemEntities(final GameTestHelper helper) {

        flowingChannel(helper, FluidPhysicsTestFluids.PLAIN_BLOCK);

        final ItemEntity[] item = new ItemEntity[1];
        final double[] startX = new double[1];

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {

                    check(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(5, 1, 2))).isEmpty(),
                            "the test fluid did not flow down the channel");

                    item[0] = helper.spawnItem(Items.STONE, 5.5F, 1.05F, 2.5F);
                    startX[0] = item[0].getX();
                })
                .thenIdle(20)
                .thenExecute(() -> check(Math.abs(item[0].getX() - startX[0]) < 0.05D,
                        "a non-pushing fluid moved the item: " + startX[0] + " -> " + item[0].getX()))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void extinguishingFluidPutsOutBurningEntities(final GameTestHelper helper) {

        floodPool(helper, FluidPhysicsTestFluids.SWIMMABLE_BLOCK, 1, 4, 1, 5, 1, 5);
        floodPool(helper, FluidPhysicsTestFluids.PLAIN_BLOCK, 1, 4, 6, 10, 1, 5);

        // Cows are not fire immune and, under the fluid surface, are out of any rain.
        final LivingEntity inSwimmable = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));
        final LivingEntity inPlain = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, 1, 3));

        inSwimmable.setSecondsOnFire(10);
        inPlain.setSecondsOnFire(10);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {

                    check(inSwimmable.getRemainingFireTicks() <= 0,
                            "an extinguishing fluid left the entity burning: " + inSwimmable.getRemainingFireTicks());
                    check(inPlain.getRemainingFireTicks() > 0,
                            "a non-extinguishing fluid put the entity out: " + inPlain.getRemainingFireTicks());
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void adjacentPathTypesMatchTheirProperties(final GameTestHelper helper) {

        for (int x = 1; x <= 8; ++x) {
            helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
        }

        helper.setBlock(new BlockPos(2, 1, 2), FluidPhysicsTestFluids.SWIMMABLE_BLOCK);
        helper.setBlock(new BlockPos(6, 1, 2), FluidPhysicsTestFluids.PLAIN_BLOCK);

        // Walkable air on stone, beside each fluid; the fluids have not spread yet.
        final BlockPathTypes besideSwimmable = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(),
                mutablePos(helper, new BlockPos(3, 1, 2)));
        final BlockPathTypes besidePlain = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(),
                mutablePos(helper, new BlockPos(7, 1, 2)));

        check(BlockPathTypes.WATER_BORDER == besideSwimmable,
                "a block beside the default-adjacent fluid has path type " + besideSwimmable);
        check(BlockPathTypes.WALKABLE == besidePlain,
                "a block beside a fluid with no adjacent path type has path type " + besidePlain);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public void vanillaWaterStillBordersPaths(final GameTestHelper helper) {

        for (int x = 1; x <= 4; ++x) {
            helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
        }

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);

        final BlockPathTypes beside = WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(),
                mutablePos(helper, new BlockPos(3, 1, 2)));

        check(BlockPathTypes.WATER_BORDER == beside, "a block beside vanilla water has path type " + beside);
        helper.succeed();
    }

    private static Mob floatingZombie(final GameTestHelper helper, final BlockPos position) {

        final Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, position);

        zombie.setNoGravity(true);
        return zombie;
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
