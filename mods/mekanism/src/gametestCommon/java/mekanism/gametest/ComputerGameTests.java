/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.LinkedHashMap;
import java.util.Map;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.Attributes.AttributeComputerIntegration;
import mekanism.common.content.matrix.MatrixMultiblockData;
import mekanism.common.integration.computer.BoundComputerMethod;
import mekanism.common.integration.computer.ComputerArgumentHandler;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.InductionCellTier;
import mekanism.common.tier.InductionProviderTier;
import mekanism.common.tile.TileEntityLogisticalSorter;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekanism.common.tile.multiblock.TileEntityInductionPort;
import mekanism.common.tile.qio.TileEntityQIOImporter;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.gametest.MultiblockStructures.Shell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Computer methods are bound when {@code mekanism.gametest.computer} is set, which the GameTest runs do. ComputerCraft
 * itself is not installed. A pump and a formed induction port are asked for the methods a computer would see, and a
 * getter is invoked with no arguments.
 */
public class ComputerGameTests {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

    @GameTest(template = TEMPLATE)
    public static void pumpExposesEnergy(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.ELECTRIC_PUMP.getBlock());
        TileEntityElectricPump pump = (TileEntityElectricPump) helper.getBlockEntity(pos);
        FloatingLong leftover = ((IStrictEnergyHandler) pump).insertEnergy(FloatingLong.createConst(20_000), Action.EXECUTE);
        FloatingLong stored = ((IStrictEnergyHandler) pump).getEnergy(0);
        FloatingLong capacity = ((IStrictEnergyHandler) pump).getMaxEnergy(0);
        check(leftover.smallerThan(FloatingLong.createConst(20_000)) && stored.greaterThan(FloatingLong.ZERO), "The pump stored no energy");

        String type = Attribute.get(MekanismBlocks.ELECTRIC_PUMP.getBlock(), AttributeComputerIntegration.class).name();
        check("electricPump".equals(type), "Pump peripheral type is " + type);
        Map<String, BoundComputerMethod> methods = methods(pump);
        check(methods.containsKey("getEnergy"), "Pump methods are " + methods.keySet());
        check(methods.containsKey("getMaxEnergy"), "Pump methods have no getMaxEnergy");
        check(methods.containsKey("getEnergyFilledPercentage"), "Pump methods have no getEnergyFilledPercentage");
        check(stored.equals(call(methods.get("getEnergy"))), "getEnergy returned " + call(methods.get("getEnergy")));
        check(capacity.equals(call(methods.get("getMaxEnergy"))), "getMaxEnergy returned " + call(methods.get("getMaxEnergy")));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void inductionPortExposesMatrixEnergy(GameTestHelper helper) {
        Shell shell = MultiblockStructures.cuboid(helper, ORIGIN, 3, 3, 4, casing(), casing(), portBlock());
        helper.setBlock(ORIGIN.offset(1, 1, 1), MekanismBlocks.BASIC_INDUCTION_CELL.getBlock());
        helper.setBlock(ORIGIN.offset(1, 1, 2), MekanismBlocks.BASIC_INDUCTION_PROVIDER.getBlock());
        int[] phase = {0};
        helper.succeedWhen(() -> {
            TileEntityInductionPort port = port(helper, shell);
            MatrixMultiblockData matrix = formed(port);
            long rate = EnergyUnit.FORGE_ENERGY.convertToAsLong(InductionProviderTier.BASIC.getOutput());
            if (phase[0] == 0) {
                long accepted = TestPlatform.insertEnergy(port, shell.valveSide(), rate);
                check(accepted == rate, "The port accepted " + accepted + " of " + rate);
                phase[0] = 1;
                check(false, "Waiting for the cell to store the energy");
            }
            FloatingLong stored = EnergyUnit.FORGE_ENERGY.convertFrom(rate);
            check(matrix.getEnergy().equals(stored), "The matrix stored " + matrix.getEnergy());
            String type = Attribute.get(port.getBlockState().getBlock(), AttributeComputerIntegration.class).name();
            check("inductionPort".equals(type), "Port peripheral type is " + type);
            Map<String, BoundComputerMethod> methods = methods(port);
            check(methods.containsKey("getEnergy"), "Port methods are " + methods.keySet());
            check(methods.containsKey("getInstalledCells"), "Port methods have no getInstalledCells");
            check(methods.containsKey("getInstalledProviders"), "Port methods have no getInstalledProviders");
            check(methods.containsKey("getTransferCap"), "Port methods have no getTransferCap");
            check(methods.containsKey("isFormed"), "Port methods have no isFormed");
            check(Boolean.TRUE.equals(call(methods.get("isFormed"))), "isFormed returned " + call(methods.get("isFormed")));
            check(Integer.valueOf(1).equals(call(methods.get("getInstalledCells"))), "getInstalledCells returned " + call(methods.get("getInstalledCells")));
            check(Integer.valueOf(1).equals(call(methods.get("getInstalledProviders"))),
                  "getInstalledProviders returned " + call(methods.get("getInstalledProviders")));
            check(stored.equals(call(methods.get("getEnergy"))), "getEnergy returned " + call(methods.get("getEnergy")));
            check(matrix.getTransferCap().equals(call(methods.get("getTransferCap"))), "getTransferCap returned " + call(methods.get("getTransferCap")));
            check(matrix.getStorageCap().equals(InductionCellTier.BASIC.getMaxEnergy()), "Storage cap changed");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sorterAndQioImporterExposeMethods(GameTestHelper helper) {
        BlockPos sorterPos = new BlockPos(1, 1, 1);
        helper.setBlock(sorterPos, MekanismBlocks.LOGISTICAL_SORTER.getBlock());
        TileEntityLogisticalSorter sorter = (TileEntityLogisticalSorter) helper.getBlockEntity(sorterPos);
        String sorterType = Attribute.get(sorter.getBlockState().getBlock(), AttributeComputerIntegration.class).name();
        check("logisticalSorter".equals(sorterType), "Sorter peripheral type is " + sorterType);
        Map<String, BoundComputerMethod> sorterMethods = methods(sorter);
        check(sorterMethods.containsKey("getAutoMode"), "Sorter methods are " + sorterMethods.keySet());
        check(sorterMethods.containsKey("isRoundRobin"), "Sorter methods have no isRoundRobin");
        check(Boolean.FALSE.equals(call(sorterMethods.get("getAutoMode"))), "getAutoMode returned " + call(sorterMethods.get("getAutoMode")));

        BlockPos importerPos = new BlockPos(3, 1, 1);
        helper.setBlock(importerPos, MekanismBlocks.QIO_IMPORTER.getBlock());
        TileEntityQIOImporter importer = (TileEntityQIOImporter) helper.getBlockEntity(importerPos);
        String importerType = Attribute.get(importer.getBlockState().getBlock(), AttributeComputerIntegration.class).name();
        check("qioImporter".equals(importerType), "Importer peripheral type is " + importerType);
        Map<String, BoundComputerMethod> importerMethods = methods(importer);
        check(importerMethods.containsKey("hasFrequency"), "Importer methods are " + importerMethods.keySet());
        check(Boolean.FALSE.equals(call(importerMethods.get("hasFrequency"))), "hasFrequency returned " + call(importerMethods.get("hasFrequency")));
        helper.succeed();
    }

    private static Map<String, BoundComputerMethod> methods(mekanism.common.integration.computer.IComputerTile tile) {
        Map<String, BoundComputerMethod> methods = new LinkedHashMap<>();
        tile.getComputerMethods(methods);
        return methods;
    }

    private static Object call(BoundComputerMethod method) {
        ComputerArgumentHandler<IllegalStateException, Object> arguments = new ComputerArgumentHandler<>() {
            @Override
            public int getCount() {
                return 0;
            }

            @Override
            public IllegalStateException error(String messageFormat, Object... args) {
                return new IllegalStateException(formatError(messageFormat, args));
            }

            @Override
            public Object getArgument(int index) {
                return null;
            }

            @Override
            public Object noResult() {
                return null;
            }

            @Override
            public Object wrapResult(Object result) {
                return result;
            }
        };
        return method.run(arguments, method.findMatchingImplementation(arguments));
    }

    private static Block casing() {
        return MekanismBlocks.INDUCTION_CASING.getBlock();
    }

    private static Block portBlock() {
        return MekanismBlocks.INDUCTION_PORT.getBlock();
    }

    private static TileEntityInductionPort port(GameTestHelper helper, Shell shell) {
        check(shell.valve() != null, "The shell has no port");
        BlockEntity blockEntity = helper.getBlockEntity(shell.valve());
        check(blockEntity instanceof TileEntityInductionPort, "The port is " + blockEntity);
        return (TileEntityInductionPort) blockEntity;
    }

    private static MatrixMultiblockData formed(TileEntityInductionPort port) {
        MatrixMultiblockData matrix = port.getMultiblock();
        check(matrix.isFormed(), "The induction matrix is not formed");
        return matrix;
    }
}
