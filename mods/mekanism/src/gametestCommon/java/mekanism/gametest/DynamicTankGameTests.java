/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.ArrayList;
import java.util.List;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.tank.TankMultiblockData;
import mekanism.common.tile.interfaces.IFluidContainerManager.ContainerEditMode;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import mekanism.gametest.MultiblockStructures.Shell;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * The dynamic tank is the multiblock the framework is checked with: a shell forms, a broken or undersized shell does
 * not, the valve moves fluid through the loader's fluid API, and the saved cache comes back after the blocks are
 * removed and replaced.
 */
public class DynamicTankGameTests {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final long STORED = 2_500;

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dynamicTankForms(GameTestHelper helper) {
        Shell shell = MultiblockStructures.dynamicTank(helper, ORIGIN, 3);
        helper.runAtTickTime(40, () -> {
            TileEntityDynamicTank valve = valve(helper, shell);
            TankMultiblockData tank = formed(valve);
            check(tank.length() == 3 && tank.width() == 3 && tank.height() == 3, "Formed tank bounds are " + tank.length() + "x" + tank.height() + "x" + tank.width());
            int volume = 3 * 3 * 3;
            check(tank.getVolume() == volume, "Formed tank volume is " + tank.getVolume());
            int capacity = volume * MekanismConfig.general.dynamicTankFluidPerTank.get();
            check(tank.getTankCapacity() == capacity, "Formed tank capacity is " + tank.getTankCapacity());
            check(tank.getValveData().size() == 1, "Formed tank has " + tank.getValveData().size() + " valves");
            TileEntityDynamicTank master = master(helper, shell);
            BlockPos min = helper.absolutePos(shell.origin());
            BlockPos max = min.offset(shell.sizeX() - 1, shell.sizeY() - 1, shell.sizeZ() - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void incompleteOrUndersizedTankDoesNotForm(GameTestHelper helper) {
        Shell holed = MultiblockStructures.dynamicTank(helper, ORIGIN, 3);
        helper.setBlock(holed.origin(), Blocks.AIR);
        Shell small = MultiblockStructures.dynamicTank(helper, new BlockPos(5, 1, 1), 2);
        helper.runAtTickTime(40, () -> {
            TileEntityDynamicTank valve = valve(helper, holed);
            check(!valve.getMultiblock().isFormed(), "A tank with a missing corner formed");
            check(TestPlatform.insertBlockFluid(valve, holed.valveSide(), Fluids.WATER, STORED, true) == 0,
                  "An unformed valve accepted fluid");
            BlockEntity casing = helper.getBlockEntity(small.origin());
            check(casing instanceof TileEntityDynamicTank, "The undersized shell has no tank block");
            check(!((TileEntityDynamicTank) casing).getMultiblock().isFormed(), "A 2x2x2 tank formed");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void valveMovesFluidThroughTheLoaderApi(GameTestHelper helper) {
        Shell shell = MultiblockStructures.dynamicTank(helper, ORIGIN, 3);
        helper.runAtTickTime(40, () -> {
            TileEntityDynamicTank valve = valve(helper, shell);
            formed(valve);
            BlockEntity casing = helper.getBlockEntity(shell.origin());
            check(TestPlatform.insertBlockFluid(casing, shell.valveSide(), Fluids.WATER, STORED, true) == 0, "A casing block accepted fluid");
            long offered = TestPlatform.insertBlockFluid(valve, shell.valveSide(), Fluids.WATER, STORED, false);
            check(offered == STORED, "A simulated fill offered " + offered + " of " + STORED);
            check(valve.getMultiblock().getFluidTank().isEmpty(), "A simulated fill stored fluid");
            long filled = TestPlatform.insertBlockFluid(valve, shell.valveSide(), Fluids.WATER, STORED, true);
            check(filled == STORED, "The valve accepted " + filled + " of " + STORED);
            check(TestPlatform.blockFluid(valve, shell.valveSide()) == STORED, "The loader reports " + TestPlatform.blockFluid(valve, shell.valveSide()));
            check(valve.getMultiblock().getFluidTank().getFluidAmount() == STORED, "The tank stores " + valve.getMultiblock().getFluidTank().getFluidAmount());
            long drained = TestPlatform.extractBlockFluid(valve, shell.valveSide(), Fluids.WATER, 500, true);
            check(drained == 500, "The valve returned " + drained);
            check(valve.getMultiblock().getFluidTank().getFluidAmount() == STORED - 500, "The tank stores " + valve.getMultiblock().getFluidTank().getFluidAmount() + " after the drain");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void tankContentsSurviveSaveAndLoad(GameTestHelper helper) {
        Shell shell = MultiblockStructures.dynamicTank(helper, ORIGIN, 3);
        helper.runAtTickTime(40, () -> {
            TileEntityDynamicTank valve = valve(helper, shell);
            TankMultiblockData tank = formed(valve);
            long filled = TestPlatform.insertBlockFluid(valve, shell.valveSide(), Fluids.WATER, STORED, true);
            check(filled == STORED, "The valve accepted " + filled + " before the save");
            tank.setContainerEditMode(ContainerEditMode.FILL);
            List<SavedBlock> saved = new ArrayList<>();
            for (BlockPos pos : shell.blocks()) {
                BlockEntity blockEntity = helper.getBlockEntity(pos);
                saved.add(new SavedBlock(pos, helper.getBlockState(pos), blockEntity.saveWithFullMetadata()));
            }
            Mekanism.tankManager.inventories.remove(tank.inventoryID);
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, Blocks.AIR);
            }
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, block.state);
                helper.getBlockEntity(block.pos).load(block.tag);
            }
        });
        helper.runAtTickTime(80, () -> {
            TileEntityDynamicTank restored = valve(helper, shell);
            TankMultiblockData data = formed(restored);
            check(data.getFluidTank().getFluid().getFluid() == Fluids.WATER && data.getFluidTank().getFluidAmount() == STORED,
                  "Reloaded tank holds " + data.getFluidTank().getFluid());
            check(data.editMode == ContainerEditMode.FILL, "Reloaded tank edit mode is " + data.editMode);
            check(TestPlatform.blockFluid(restored, shell.valveSide()) == STORED, "The loader reports " + TestPlatform.blockFluid(restored, shell.valveSide()) + " after reload");
            helper.succeed();
        });
    }

    private static TileEntityDynamicTank valve(GameTestHelper helper, Shell shell) {
        check(shell.valve() != null, "The shell has no valve");
        BlockEntity blockEntity = helper.getBlockEntity(shell.valve());
        check(blockEntity instanceof TileEntityDynamicTank, "The valve is " + blockEntity);
        return (TileEntityDynamicTank) blockEntity;
    }

    private static TankMultiblockData formed(TileEntityDynamicTank tile) {
        TankMultiblockData tank = tile.getMultiblock();
        check(tank.isFormed(), "The dynamic tank is not formed");
        return tank;
    }

    private static TileEntityDynamicTank master(GameTestHelper helper, Shell shell) {
        TileEntityDynamicTank master = null;
        for (BlockPos pos : shell.blocks()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntityDynamicTank tank && tank.isMaster()) {
                check(master == null, "The tank has two masters");
                master = tank;
            }
        }
        check(master != null, "The formed tank has no master");
        return master;
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}
