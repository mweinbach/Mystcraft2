/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.math.FloatingLong;
import mekanism.common.Mekanism;
import mekanism.common.content.matrix.MatrixMultiblockData;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tier.InductionCellTier;
import mekanism.common.tier.InductionProviderTier;
import mekanism.common.tile.multiblock.TileEntityInductionCasing;
import mekanism.common.tile.multiblock.TileEntityInductionCell;
import mekanism.common.tile.multiblock.TileEntityInductionPort;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.gametest.MultiblockStructures.Shell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A 3×3×4 induction matrix with one basic cell and one basic provider. The port moves energy through the loader's
 * energy API, an energy tablet in the port's charge slot takes energy out of the matrix, and the cell still holds
 * that energy after its block is saved and loaded.
 */
public class InductionMatrixGameTests {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos CELL = ORIGIN.offset(1, 1, 1);
    private static final BlockPos PROVIDER = ORIGIN.offset(1, 1, 2);

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void inductionMatrixForms(GameTestHelper helper) {
        Shell shell = matrix(helper, ORIGIN);
        helper.runAtTickTime(40, () -> {
            TileEntityInductionPort port = port(helper, shell);
            MatrixMultiblockData matrix = formed(port);
            check(matrix.length() == 3 && matrix.height() == 3 && matrix.width() == 4,
                  "Formed matrix bounds are " + matrix.length() + "x" + matrix.height() + "x" + matrix.width());
            check(matrix.getVolume() == 3 * 3 * 4, "Formed matrix volume is " + matrix.getVolume());
            check(matrix.getCellCount() == 1, "Formed matrix has " + matrix.getCellCount() + " cells");
            check(matrix.getProviderCount() == 1, "Formed matrix has " + matrix.getProviderCount() + " providers");
            check(matrix.getStorageCap().equals(InductionCellTier.BASIC.getMaxEnergy()), "Formed matrix stores " + matrix.getStorageCap());
            check(matrix.getTransferCap().equals(InductionProviderTier.BASIC.getOutput()), "Formed matrix transfers " + matrix.getTransferCap());
            check(matrix.getValveData().size() == 1, "Formed matrix has " + matrix.getValveData().size() + " ports");
            check(!port.getActive(), "A new port is in output mode");
            BlockEntity cell = helper.getBlockEntity(CELL);
            check(cell instanceof TileEntityInductionCell, "The interior cell is " + cell);
            TileEntityInductionCasing master = master(helper, shell);
            BlockPos min = helper.absolutePos(shell.origin());
            BlockPos max = min.offset(shell.sizeX() - 1, shell.sizeY() - 1, shell.sizeZ() - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void incompleteOrUndersizedMatrixDoesNotForm(GameTestHelper helper) {
        Shell holed = matrix(helper, ORIGIN);
        helper.setBlock(holed.origin(), Blocks.AIR);
        Shell small = MultiblockStructures.cuboid(helper, new BlockPos(5, 1, 1), 2, 2, 2, casing(), casing(), null);
        Shell blocked = MultiblockStructures.cuboid(helper, new BlockPos(1, 1, 5), 3, 3, 3, casing(), casing(), portBlock());
        helper.setBlock(new BlockPos(2, 2, 6), Blocks.STONE);
        helper.runAtTickTime(40, () -> {
            TileEntityInductionPort port = port(helper, holed);
            check(!port.getMultiblock().isFormed(), "A matrix with a missing corner formed");
            check(TestPlatform.insertEnergy(port, holed.valveSide(), rateFe()) == 0, "An unformed port accepted energy");
            BlockEntity casing = helper.getBlockEntity(small.origin());
            check(casing instanceof TileEntityInductionCasing, "The undersized shell has no induction casing");
            check(!((TileEntityInductionCasing) casing).getMultiblock().isFormed(), "A 2x2x2 matrix formed");
            TileEntityInductionPort blockedPort = port(helper, blocked);
            check(!blockedPort.getMultiblock().isFormed(), "A matrix with stone inside formed");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void portMovesEnergyThroughTheLoaderApi(GameTestHelper helper) {
        Shell shell = matrix(helper, ORIGIN);
        helper.runAtTickTime(40, () -> {
            TileEntityInductionPort port = port(helper, shell);
            MatrixMultiblockData matrix = formed(port);
            Direction side = shell.valveSide();
            BlockEntity casing = helper.getBlockEntity(shell.origin());
            long rate = rateFe();
            check(TestPlatform.insertEnergy(casing, side, rate) == 0, "A casing block accepted energy");
            long accepted = TestPlatform.insertEnergy(port, side, rate * 4);
            check(accepted == rate, "The input port accepted " + accepted + " of a " + rate + " FE rate");
            check(matrix.getEnergy().equals(EnergyUnit.FORGE_ENERGY.convertFrom(accepted)), "The matrix stored " + matrix.getEnergy());
            check(TestPlatform.extractEnergy(port, side, rate) == 0, "An input port returned energy");
            port.setActive(true);
            check(TestPlatform.insertEnergy(port, side, rate) == 0, "An output port accepted energy");
            long extracted = TestPlatform.extractEnergy(port, side, rate);
            check(extracted == rate, "The output port returned " + extracted + " of " + rate);
            check(matrix.getEnergy().isZero(), "The matrix still holds " + matrix.getEnergy() + " after the output");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void portChargesAnEnergyTablet(GameTestHelper helper) {
        Shell shell = matrix(helper, ORIGIN);
        helper.runAtTickTime(40, () -> {
            TileEntityInductionPort port = port(helper, shell);
            formed(port);
            long accepted = TestPlatform.insertEnergy(port, shell.valveSide(), rateFe());
            check(accepted == rateFe(), "The port accepted " + accepted + " before the tablet was inserted");
            ItemStack tablet = new ItemStack(MekanismItems.ENERGY_TABLET.get());
            check(TestPlatform.itemEnergy(tablet) == 0, "A new energy tablet already holds energy");
            long inserted = TestPlatform.insertItems(port, shell.valveSide(), tablet);
            check(inserted == 1, "The port accepted " + inserted + " energy tablets");
            check(TestPlatform.insertItems(port, shell.valveSide(), new ItemStack(Items.COBBLESTONE)) == 0, "The port accepted cobblestone");
        });
        helper.runAtTickTime(60, () -> {
            TileEntityInductionPort port = port(helper, shell);
            MatrixMultiblockData matrix = formed(port);
            ItemStack stored = port.getInventorySlots(null).get(0).getStack();
            check(stored.is(MekanismItems.ENERGY_TABLET.get()), "The charge slot holds " + stored);
            FloatingLong tablet = EnergyUnit.FORGE_ENERGY.convertFrom(TestPlatform.itemEnergy(stored));
            FloatingLong total = matrix.getEnergy().add(tablet);
            FloatingLong inserted = EnergyUnit.FORGE_ENERGY.convertFrom(rateFe());
            check(tablet.greaterThan(FloatingLong.ZERO), "The energy tablet was not charged");
            check(total.equals(inserted), "Matrix " + matrix.getEnergy() + " plus tablet " + tablet + " is not the inserted " + inserted);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void matrixEnergySurvivesSaveAndLoad(GameTestHelper helper) {
        Shell shell = matrix(helper, ORIGIN);
        helper.runAtTickTime(40, () -> {
            TileEntityInductionPort port = port(helper, shell);
            formed(port);
            long accepted = TestPlatform.insertEnergy(port, shell.valveSide(), rateFe());
            check(accepted == rateFe(), "The port accepted " + accepted + " before the save");
        });
        helper.runAtTickTime(50, () -> {
            TileEntityInductionPort port = port(helper, shell);
            MatrixMultiblockData matrix = formed(port);
            FloatingLong stored = EnergyUnit.FORGE_ENERGY.convertFrom(rateFe());
            check(matrix.getEnergy().equals(stored), "The cell had not stored " + stored + " before the save: " + matrix.getEnergy());
            BlockEntity cell = helper.getBlockEntity(CELL);
            check(cell instanceof TileEntityInductionCell, "The cell is " + cell);
            check(((TileEntityInductionCell) cell).getEnergyContainer().getEnergy().equals(stored),
                  "The cell block stores " + ((TileEntityInductionCell) cell).getEnergyContainer().getEnergy());
            List<SavedBlock> saved = new ArrayList<>();
            List<BlockPos> blocks = new ArrayList<>(shell.blocks());
            blocks.add(CELL);
            blocks.add(PROVIDER);
            for (BlockPos pos : blocks) {
                BlockEntity blockEntity = helper.getBlockEntity(pos);
                saved.add(new SavedBlock(pos, helper.getBlockState(pos), blockEntity.saveWithFullMetadata()));
            }
            Mekanism.matrixManager.inventories.remove(matrix.inventoryID);
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, Blocks.AIR);
            }
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, block.state);
                helper.getBlockEntity(block.pos).load(block.tag);
            }
        });
        helper.runAtTickTime(100, () -> {
            TileEntityInductionPort restored = port(helper, shell);
            MatrixMultiblockData data = formed(restored);
            FloatingLong stored = EnergyUnit.FORGE_ENERGY.convertFrom(rateFe());
            check(data.getEnergy().equals(stored), "Reloaded matrix holds " + data.getEnergy());
            check(data.getCellCount() == 1 && data.getProviderCount() == 1, "Reloaded matrix has " + data.getCellCount() + " cells and " + data.getProviderCount() + " providers");
            restored.setActive(true);
            long extracted = TestPlatform.extractEnergy(restored, shell.valveSide(), rateFe());
            check(extracted == rateFe(), "The reloaded port returned " + extracted);
            check(data.getEnergy().isZero(), "The reloaded matrix still holds " + data.getEnergy());
            helper.succeed();
        });
    }

    private static Shell matrix(GameTestHelper helper, BlockPos origin) {
        Shell shell = MultiblockStructures.cuboid(helper, origin, 3, 3, 4, casing(), casing(), portBlock());
        helper.setBlock(origin.offset(1, 1, 1), MekanismBlocks.BASIC_INDUCTION_CELL.getBlock());
        helper.setBlock(origin.offset(1, 1, 2), MekanismBlocks.BASIC_INDUCTION_PROVIDER.getBlock());
        return shell;
    }

    private static Block casing() {
        return MekanismBlocks.INDUCTION_CASING.getBlock();
    }

    private static Block portBlock() {
        return MekanismBlocks.INDUCTION_PORT.getBlock();
    }

    private static long rateFe() {
        return EnergyUnit.FORGE_ENERGY.convertToAsLong(InductionProviderTier.BASIC.getOutput());
    }

    private static TileEntityInductionPort port(GameTestHelper helper, Shell shell) {
        check(shell.valve() != null, "The shell has no port");
        BlockEntity blockEntity = helper.getBlockEntity(shell.valve());
        check(blockEntity instanceof TileEntityInductionPort, "The port is " + blockEntity);
        return (TileEntityInductionPort) blockEntity;
    }

    private static MatrixMultiblockData formed(TileEntityInductionCasing tile) {
        MatrixMultiblockData matrix = tile.getMultiblock();
        check(matrix.isFormed(), "The induction matrix is not formed");
        return matrix;
    }

    private static TileEntityInductionCasing master(GameTestHelper helper, Shell shell) {
        TileEntityInductionCasing master = null;
        for (BlockPos pos : shell.blocks()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntityInductionCasing casing && casing.isMaster()) {
                check(master == null, "The matrix has two masters");
                master = casing;
            }
        }
        check(master != null, "The formed matrix has no master");
        return master;
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}
