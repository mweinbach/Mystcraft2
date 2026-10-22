/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.common.config.MekanismConfig;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.laser.TileEntityLaser;
import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import mekanism.common.tile.laser.TileEntityLaserAmplifier.RedstoneOutput;
import mekanism.common.tile.laser.TileEntityLaserTractorBeam;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.gametest.FusionReactorGameTests.Reactor;
import mekanism.generators.common.content.fusion.FusionReactorMultiblockData;
import mekanism.generators.common.tile.fusion.TileEntityFusionReactorController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * A laser spends its per-tick usage digging a block and damaging a mob. An amplifier holds that energy until its
 * minimum threshold and caps each shot at its maximum, and its redstone output follows the selected mode. A tractor
 * beam keeps the broken block's drop. A ray into a fusion reactor's focus matrix heats the controller's plasma.
 */
public class LaserGameTests {

    private static final BlockPos LASER = new BlockPos(1, 2, 1);
    private static final BlockPos TARGET = new BlockPos(3, 2, 1);
    /** Above the amplifier's buffer while it is only being filled. */
    private static final FloatingLong CLOSED = FloatingLong.createConst(1_000_000_000L);

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void laserBreaksABlock(GameTestHelper helper) {
        helper.setBlock(LASER, MekanismBlocks.LASER.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(TARGET, Blocks.DIRT);
        TileEntityLaser laser = laser(helper, LASER);
        long fe = breakEnergyFe(helper, TARGET);
        long accepted = TestPlatform.insertEnergy(laser, laser.getDirection().getOpposite(), fe);
        check(accepted == fe, "The laser accepted " + accepted + " of " + fe + " through its back");
        helper.succeedWhen(() -> {
            check(helper.getBlockState(TARGET).isAir(), "The laser left " + helper.getBlockState(TARGET));
            BlockPos absolute = helper.absolutePos(TARGET);
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3));
            check(drops.stream().anyMatch(entity -> entity.getItem().is(Items.DIRT) && !entity.isRemoved()),
                  "The broken dirt left " + drops);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void laserDamagesAnEntity(GameTestHelper helper) {
        helper.setBlock(LASER, MekanismBlocks.LASER.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
        TileEntityLaser laser = laser(helper, LASER);
        long accepted = TestPlatform.insertEnergy(laser, Direction.WEST, 100_000);
        check(accepted > 0, "The laser accepted no energy");
        Pig pig = helper.spawn(EntityType.PIG, 3, 2, 1);
        pig.setNoAi(true);
        float health = pig.getHealth();
        helper.succeedWhen(() -> {
            check(pig.getHealth() < health, "The pig stayed at " + pig.getHealth() + " health");
            check(pig.getRemainingFireTicks() > 0 || !pig.isAlive(), "The laser left the pig unlit at " + pig.getHealth() + " health");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void amplifierStoresAndFires(GameTestHelper helper) {
        BlockPos amplifierPos = new BlockPos(2, 2, 1);
        helper.setBlock(LASER, MekanismBlocks.LASER.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(amplifierPos, MekanismBlocks.LASER_AMPLIFIER.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(TARGET, Blocks.DIRT);
        TileEntityLaser laser = laser(helper, LASER);
        TileEntityLaserAmplifier amplifier = amplifier(helper, amplifierPos);
        amplifier.setMinThresholdFromPacket(CLOSED);
        FloatingLong needed = breakEnergy(helper, TARGET);
        int[] phase = new int[1];
        FloatingLong[] held = new FloatingLong[1];
        helper.succeedWhen(() -> {
            FloatingLong stored = amplifier.getEnergyContainer().getEnergy().copy();
            if (phase[0] == 0) {
                long accepted = TestPlatform.insertEnergy(laser, Direction.WEST, 800_000);
                check(accepted > 0, "The feeding laser accepted no energy");
                phase[0] = 1;
                check(false, "Waiting for the amplifier to store a shot");
            }
            if (phase[0] == 1) {
                check(helper.getBlockState(TARGET).is(Blocks.DIRT), "Dirt broke while the buffer was below its minimum");
                if (stored.smallerThan(needed)) {
                    check(false, "The amplifier holds " + stored + " of " + needed);
                }
                laser.getEnergyContainer().extract(laser.getEnergyContainer().getEnergy(), Action.EXECUTE, AutomationType.INTERNAL);
                held[0] = stored;
                phase[0] = 2;
                check(false, "Waiting to see the buffer hold");
            }
            if (phase[0] == 2) {
                check(stored.equals(held[0]), "Stored energy changed from " + held[0] + " to " + stored + " below the minimum");
                check(helper.getBlockState(TARGET).is(Blocks.DIRT), "Dirt broke below the minimum threshold");
                amplifier.setMinThresholdFromPacket(FloatingLong.ZERO);
                amplifier.setMaxThresholdFromPacket(FloatingLong.ONE);
                phase[0] = 3;
                check(false, "Waiting for a one-joule shot");
            }
            if (phase[0] == 3) {
                FloatingLong spent = held[0].subtract(stored);
                check(spent.equals(FloatingLong.ONE), "A one-joule cap spent " + spent);
                check(helper.getBlockState(TARGET).is(Blocks.DIRT), "One joule broke the dirt");
                amplifier.setMaxThresholdFromPacket(held[0]);
                phase[0] = 4;
                check(false, "Waiting for the amplifier to break the dirt");
            }
            check(helper.getBlockState(TARGET).isAir(), "The amplifier left " + helper.getBlockState(TARGET));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void amplifierRedstoneModes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, MekanismBlocks.LASER_AMPLIFIER.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.STONE);
        TileEntityLaserAmplifier amplifier = amplifier(helper, pos);
        amplifier.setMinThresholdFromPacket(CLOSED);
        amplifier.nextMode();
        amplifier.nextMode();
        check(amplifier.getOutputMode() == RedstoneOutput.ENERGY_CONTENTS, "The second mode is " + amplifier.getOutputMode());
        amplifier.receiveLaserEnergy(CLOSED);
        int contents = MekanismUtils.redstoneLevelFromContents(amplifier.getEnergyContainer().getEnergy(), amplifier.getEnergyContainer().getMaxEnergy());
        check(contents > 1, "A full tenth of the buffer reported redstone " + contents);
        check(signal(helper, pos) == contents, "Energy contents output " + signal(helper, pos) + " instead of " + contents);
        amplifier.nextMode();
        check(amplifier.getOutputMode() == RedstoneOutput.OFF, "The next mode is " + amplifier.getOutputMode());
        check(signal(helper, pos) == 0, "Off mode output " + signal(helper, pos) + " with a full buffer");
        amplifier.nextMode();
        check(amplifier.getOutputMode() == RedstoneOutput.ENTITY_DETECTION, "The detection mode is " + amplifier.getOutputMode());
        amplifier.setMinThresholdFromPacket(FloatingLong.ZERO);
        amplifier.setMaxThresholdFromPacket(MekanismConfig.usage.laser.get());
        Pig pig = helper.spawn(EntityType.PIG, 2, 2, 4);
        pig.setNoAi(true);
        pig.setInvulnerable(true);
        helper.succeedWhen(() -> check(signal(helper, pos) == 15,
              "Entity detection output " + signal(helper, pos) + " at pig " + pig.position() + " health " + pig.getHealth()));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void tractorBeamCollectsTheDrop(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 2);
        BlockPos target = new BlockPos(2, 2, 2);
        helper.setBlock(pos, MekanismBlocks.LASER_TRACTOR_BEAM.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(target, Blocks.DIRT);
        TileEntityLaserTractorBeam beam = tractor(helper, pos);
        beam.receiveLaserEnergy(breakEnergy(helper, target));
        helper.succeedWhen(() -> {
            check(helper.getBlockState(target).isAir(), "The tractor beam left " + helper.getBlockState(target));
            long collected = 0;
            for (IInventorySlot slot : beam.getInventorySlots(null)) {
                if (slot.getStack().is(Items.DIRT)) {
                    collected += slot.getCount();
                }
            }
            check(collected == 1, "The tractor beam inventory holds " + collected + " dirt");
            BlockPos absolute = helper.absolutePos(target);
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3));
            check(drops.stream().noneMatch(entity -> entity.getItem().is(Items.DIRT) && !entity.isRemoved()),
                  "Dirt was also left in the world: " + drops);
        });
    }

    @GameTest(template = "mekanism:fusion_empty", timeoutTicks = 300)
    public static void laserEnergyReachesTheController(GameTestHelper helper) {
        Reactor reactor = FusionReactorGameTests.placeValid(helper, new BlockPos(1, 1, 1));
        BlockPos laserPos = reactor.matrix().below();
        helper.setBlock(laserPos, MekanismBlocks.LASER.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        double[] cold = {Double.NaN};
        helper.succeedWhen(() -> {
            BlockEntity controllerEntity = helper.getBlockEntity(reactor.controller());
            check(controllerEntity instanceof TileEntityFusionReactorController, "The controller is " + controllerEntity);
            TileEntityFusionReactorController controller = (TileEntityFusionReactorController) controllerEntity;
            FusionReactorMultiblockData data = controller.getMultiblock();
            check(data.isFormed(), "The fusion reactor is not formed");
            check(controller.isMaster(), "The controller is not the master of the reactor the laser heats");
            if (Double.isNaN(cold[0])) {
                cold[0] = data.getPlasmaTemp();
                TileEntityLaser laser = laser(helper, laserPos);
                long charged = TestPlatform.insertEnergy(laser, laser.getDirection().getOpposite(), 2_000_000);
                check(charged >= 10_000, "The laser accepted " + charged + " through its back");
                check(false, "Waiting for laser energy to reach the controller");
            }
            check(data.getPlasmaTemp() > cold[0] + 500,
                  "The controller's plasma moved from " + cold[0] + " to " + data.getPlasmaTemp());
        });
    }

    private static FloatingLong breakEnergy(GameTestHelper helper, BlockPos target) {
        float hardness = helper.getBlockState(target).getDestroySpeed(helper.getLevel(), helper.absolutePos(target));
        check(hardness > 0, "The target hardness is " + hardness);
        return MekanismConfig.general.laserEnergyNeededPerHardness.get().multiply(hardness);
    }

    /** Forge Energy, or Team Reborn Energy, for {@link #breakEnergy}. */
    private static long breakEnergyFe(GameTestHelper helper, BlockPos target) {
        long fe = EnergyUnit.FORGE_ENERGY.convertToAsLong(breakEnergy(helper, target));
        check(fe > 0, "Break energy converted to " + fe);
        return fe;
    }

    private static int signal(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getSignal(helper.absolutePos(pos), Direction.UP);
    }

    private static TileEntityLaser laser(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityLaser, "The laser is " + blockEntity);
        return (TileEntityLaser) blockEntity;
    }

    private static TileEntityLaserAmplifier amplifier(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityLaserAmplifier, "The amplifier is " + blockEntity);
        return (TileEntityLaserAmplifier) blockEntity;
    }

    private static TileEntityLaserTractorBeam tractor(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityLaserTractorBeam, "The tractor beam is " + blockEntity);
        return (TileEntityLaserTractorBeam) blockEntity;
    }
}
