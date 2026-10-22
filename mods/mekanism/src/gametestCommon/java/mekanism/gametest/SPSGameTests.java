/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.math.FloatingLong;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.sps.SPSMultiblockData;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.tile.multiblock.TileEntitySPSCasing;
import mekanism.common.tile.multiblock.TileEntitySPSPort;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.gametest.MultiblockStructures.Shell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A 7×7×7 supercritical phase shifter. The face pattern is the one in {@code SPSValidator}: frame on the marked
 * edges, casing or ports elsewhere, and the twelve corner cells of each face left as air. One port has a
 * supercharged coil on its inner side. Polonium enters and antimatter leaves through the gas handler. Only that
 * coil's port feeds energy into the reaction.
 */
public class SPSGameTests {

    private static final String YARD = "mekanism:sps_yard";
    private static final int SIZE = 7;
    /** Same cells as {@code SPSValidator}'s allowed grid: 0 ignored, 1 frame, 2 casing or port. */
    private static final byte[][] GRID = {
          {0, 0, 1, 1, 1, 0, 0},
          {0, 1, 2, 2, 2, 1, 0},
          {1, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 1},
          {0, 1, 2, 2, 2, 1, 0},
          {0, 0, 1, 1, 1, 0, 0}
    };
    private static final BlockPos COIL_PORT = new BlockPos(3, 3, 0);
    private static final BlockPos COIL = COIL_PORT.relative(Direction.SOUTH);
    private static final BlockPos IDLE_PORT = new BlockPos(3, 3, 6);
    private static final BlockPos HOLE = new BlockPos(3, 3, 0);
    private static final BlockPos HOLED_PORT = new BlockPos(4, 3, 0);

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void supercriticalPhaseShifterForms(GameTestHelper helper) {
        Sps sps = placeFormed(helper, BlockPos.ZERO);
        helper.succeedWhen(() -> {
            TileEntitySPSPort coil = port(helper, sps.coilPort());
            if (!coil.getMultiblock().isFormed()) {
                throw new GameTestAssertException("The SPS has not formed");
            }
            SPSMultiblockData data = formed(coil);
            check(data.length() == SIZE && data.height() == SIZE && data.width() == SIZE,
                  "Formed SPS bounds are " + data.length() + "x" + data.height() + "x" + data.width());
            check(data.locations.size() == sps.shell().size(), "The shell has " + data.locations.size() + " blocks");
            check(data.getValveData().size() == 2, "Formed SPS has " + data.getValveData().size() + " ports");
            check(data.coilData.coilMap.size() == 1, "Formed SPS has " + data.coilData.coilMap.size() + " coils");
            check(data.coilData.coilMap.containsKey(helper.absolutePos(sps.coilPort())), "The coil is not tied to its port");
            int per = MekanismConfig.general.spsInputPerAntimatter.get();
            check(data.inputTank.getCapacity() == per * 2L, "Input capacity is " + data.inputTank.getCapacity());
            check(data.outputTank.getCapacity() == MekanismConfig.general.spsOutputTankCapacity.get(),
                  "Output capacity is " + data.outputTank.getCapacity());
            Direction side = sps.coilSide();
            check(TestPlatform.gasTanks(coil, side) == 2, "The gas lookup exposes " + TestPlatform.gasTanks(coil, side) + " tanks");
            check(!coil.getActive(), "A new port is in output mode");
            BlockEntity casing = helper.getBlockEntity(sps.casing());
            check(TestPlatform.insertBlockGas(casing, side, MekanismGases.POLONIUM.get(), per, true) == 0, "A casing block accepted polonium");
            check(TestPlatform.insertEnergy(casing, side, 1_000) == 0, "A casing block accepted energy");
            long room = data.inputTank.getCapacity();
            check(TestPlatform.insertBlockGas(coil, side, MekanismGases.POLONIUM.get(), room, false) == room,
                  "A simulated fill offered less than the input tank");
            check(data.inputTank.isEmpty(), "A simulated fill stored polonium");
            TileEntitySPSCasing master = master(helper, sps);
            BlockPos min = helper.absolutePos(sps.origin());
            BlockPos max = min.offset(SIZE - 1, SIZE - 1, SIZE - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
        });
    }

    @GameTest(template = YARD, timeoutTicks = 200)
    public static void invalidSpsShapesStayApart(GameTestHelper helper) {
        // One block of air between shells. Face-adjacent SPS casings merge, and that assembly fails the cuboid check
        // before the missing-block and disconnected-coil rules run.
        Sps holed = placeHoled(helper, new BlockPos(0, 0, 0));
        Sps coil = placeDisconnectedCoil(helper, new BlockPos(8, 0, 0));
        Shell small = MultiblockStructures.cuboid(helper, new BlockPos(0, 0, 8), 3, 3, 3, casingBlock(), casingBlock(), portBlock());
        Shell tiny = MultiblockStructures.cuboid(helper, new BlockPos(4, 0, 8), 2, 2, 2, casingBlock(), casingBlock(), portBlock());
        helper.succeedWhen(() -> {
            separate(helper, holed.holedPort(), holed.shell().size(), "A shell missing a face block");
            check(TestPlatform.gasTanks(port(helper, holed.holedPort()), holed.holedSide()) == 0, "An unformed port exposes a gas tank");
            check(TestPlatform.insertBlockGas(port(helper, holed.holedPort()), holed.holedSide(), MekanismGases.POLONIUM.get(), 1, true) == 0,
                  "An unformed port accepted polonium");
            separate(helper, coil.casing(), coil.shell().size(), "A shell whose coil touches no port");
            separate(helper, small.origin(), small.blocks().size(), "A 3x3x3 SPS");
            separate(helper, tiny.origin(), tiny.blocks().size(), "A 2x2x2 SPS");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void coilPortConvertsPoloniumToAntimatter(GameTestHelper helper) {
        Sps sps = placeFormed(helper, BlockPos.ZERO);
        int[] phase = {0};
        int[] idleTicks = {0};
        long[] polonium = {0};
        helper.succeedWhen(() -> {
            TileEntitySPSPort coil = port(helper, sps.coilPort());
            if (phase[0] == 0) {
                if (!coil.getMultiblock().isFormed()) {
                    throw new GameTestAssertException("The SPS has not formed");
                }
                SPSMultiblockData data = formed(coil);
                int per = MekanismConfig.general.spsInputPerAntimatter.get();
                long input = per + per / 4L;
                Direction side = sps.coilSide();
                BlockEntity casing = helper.getBlockEntity(sps.casing());
                check(TestPlatform.insertBlockGas(casing, side, MekanismGases.POLONIUM.get(), input, true) == 0, "A casing block accepted polonium");
                check(TestPlatform.insertBlockGas(coil, side, MekanismGases.HYDROGEN.get(), input, true) == 0, "The port accepted hydrogen");
                check(TestPlatform.insertBlockGas(coil, side, MekanismGases.ANTIMATTER.get(), 1, true) == 0, "The port accepted antimatter");
                check(TestPlatform.insertBlockGas(coil, side, MekanismGases.POLONIUM.get(), input, false) == input,
                      "A simulated fill offered less polonium than requested");
                check(data.inputTank.isEmpty(), "A simulated fill stored polonium");
                polonium[0] = TestPlatform.insertBlockGas(coil, side, MekanismGases.POLONIUM.get(), input, true);
                check(polonium[0] == input, "The port accepted " + polonium[0] + " of " + input + " polonium");
                check(TestPlatform.blockGas(coil, side) == input, "The gas lookup reports " + TestPlatform.blockGas(coil, side));
                long fe = oneAntimatterFe();
                TileEntitySPSPort idle = port(helper, sps.idlePort());
                long idleAccepted = TestPlatform.insertEnergy(idle, sps.idleSide(), fe);
                check(idleAccepted == fe, "The port with no coil accepted " + idleAccepted + " of " + fe + " FE");
                check(TestPlatform.insertEnergy(casing, side, fe) == 0, "A casing block accepted energy");
                phase[0] = 1;
                throw new GameTestAssertException("Watching the port that has no coil");
            }
            SPSMultiblockData data = formed(coil);
            TileEntitySPSPort idle = port(helper, sps.idlePort());
            if (phase[0] == 1) {
                check(data.outputTank.isEmpty(), "A port with no coil produced antimatter");
                check(idle.getEnergyContainers(null).get(0).getEnergy().greaterThan(FloatingLong.ZERO),
                      "The port with no coil lost its energy");
                if (++idleTicks[0] < 5) {
                    throw new GameTestAssertException("The port with no coil has been watched for " + idleTicks[0] + " ticks");
                }
                long fe = oneAntimatterFe();
                long accepted = TestPlatform.insertEnergy(coil, sps.coilSide(), fe);
                check(accepted == fe, "The coil's port accepted " + accepted + " of " + fe + " FE");
                phase[0] = 2;
                throw new GameTestAssertException("Waiting for antimatter");
            }
            int per = MekanismConfig.general.spsInputPerAntimatter.get();
            if (data.outputTank.getStored() < 1) {
                throw new GameTestAssertException("The SPS holds " + data.outputTank.getStored() + " mB of antimatter and "
                      + data.inputTank.getStored() + " mB of polonium");
            }
            check(data.outputTank.getStack().getType() == MekanismGases.ANTIMATTER.get(), "The output tank holds " + data.outputTank.getStack());
            check(data.outputTank.getStored() == 1, "The SPS produced " + data.outputTank.getStored() + " mB of antimatter");
            check(data.inputTank.getStored() == polonium[0] - per, "Polonium left is " + data.inputTank.getStored());
            check(data.inputTank.getStack().getType() == MekanismGases.POLONIUM.get(), "The input tank holds " + data.inputTank.getStack());
            GasStack simulated = TestPlatform.extractGas(coil, sps.coilSide(), 1, false);
            check(simulated.getType() == MekanismGases.ANTIMATTER.get() && simulated.getAmount() == 1, "A simulated extract returned " + simulated);
            check(data.outputTank.getStored() == 1, "A simulated extract removed antimatter");
            GasStack taken = TestPlatform.extractGas(coil, sps.coilSide(), 1, true);
            check(taken.getType() == MekanismGases.ANTIMATTER.get() && taken.getAmount() == 1, "The gas lookup returned " + taken);
            check(data.outputTank.isEmpty(), "Antimatter remained after the extract");
            check(idle.getEnergyContainers(null).get(0).getEnergy().greaterThan(FloatingLong.ZERO),
                  "Powering the coil drained the other port");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void spsContentsSurviveSaveAndLoad(GameTestHelper helper) {
        Sps sps = placeFormed(helper, BlockPos.ZERO);
        int[] phase = {0};
        long[] polonium = {0};
        long[] antimatter = {0};
        FloatingLong[] idleEnergy = {FloatingLong.ZERO};
        helper.succeedWhen(() -> {
            TileEntitySPSPort coil = port(helper, sps.coilPort());
            if (phase[0] == 0) {
                if (!coil.getMultiblock().isFormed()) {
                    throw new GameTestAssertException("The SPS has not formed");
                }
                int per = MekanismConfig.general.spsInputPerAntimatter.get();
                long input = per + per / 4L;
                long filled = TestPlatform.insertBlockGas(coil, sps.coilSide(), MekanismGases.POLONIUM.get(), input, true);
                check(filled == input, "The port accepted " + filled + " polonium before the save");
                long fe = oneAntimatterFe();
                TileEntitySPSPort idle = port(helper, sps.idlePort());
                long idleAccepted = TestPlatform.insertEnergy(idle, sps.idleSide(), fe);
                check(idleAccepted == fe, "The port with no coil accepted " + idleAccepted + " before the save");
                long accepted = TestPlatform.insertEnergy(coil, sps.coilSide(), fe);
                check(accepted == fe, "The coil's port accepted " + accepted + " before the save");
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for antimatter before the save");
            }
            if (phase[0] == 1) {
                SPSMultiblockData data = formed(coil);
                if (data.outputTank.getStored() < 1) {
                    throw new GameTestAssertException("The SPS produced no antimatter before the save");
                }
                polonium[0] = data.inputTank.getStored();
                antimatter[0] = data.outputTank.getStored();
                idleEnergy[0] = port(helper, sps.idlePort()).getEnergyContainers(null).get(0).getEnergy().copy();
                check(polonium[0] > 0, "The input tank was empty before the save");
                check(idleEnergy[0].greaterThan(FloatingLong.ZERO), "The port with no coil was empty before the save");
                List<SavedBlock> saved = new ArrayList<>();
                for (BlockPos pos : sps.shell()) {
                    saved.add(save(helper, pos));
                }
                saved.add(save(helper, sps.coil()));
                Mekanism.spsManager.inventories.remove(data.inventoryID);
                for (SavedBlock block : saved) {
                    helper.setBlock(block.pos(), Blocks.AIR);
                }
                for (SavedBlock block : saved) {
                    helper.setBlock(block.pos(), block.state());
                    helper.getBlockEntity(block.pos()).load(block.tag());
                }
                phase[0] = 2;
                throw new GameTestAssertException("Waiting for the reloaded SPS");
            }
            if (!coil.getMultiblock().isFormed()) {
                throw new GameTestAssertException("The reloaded SPS has not formed");
            }
            SPSMultiblockData data = formed(coil);
            check(data.inputTank.getStack().getType() == MekanismGases.POLONIUM.get(), "Reloaded polonium is " + data.inputTank.getStack());
            check(data.inputTank.getStored() == polonium[0], "Reloaded SPS holds " + data.inputTank.getStored() + " mB of polonium");
            check(data.outputTank.getStack().getType() == MekanismGases.ANTIMATTER.get(), "Reloaded antimatter is " + data.outputTank.getStack());
            check(data.outputTank.getStored() == antimatter[0], "Reloaded SPS holds " + data.outputTank.getStored() + " mB of antimatter");
            check(TestPlatform.blockGas(coil, sps.coilSide()) == polonium[0] + antimatter[0],
                  "The gas lookup reports " + TestPlatform.blockGas(coil, sps.coilSide()) + " after reload");
            FloatingLong restored = port(helper, sps.idlePort()).getEnergyContainers(null).get(0).getEnergy();
            check(restored.equals(idleEnergy[0]), "The reloaded port with no coil holds " + restored);
            GasStack taken = TestPlatform.extractGas(coil, sps.coilSide(), antimatter[0], true);
            check(taken.getType() == MekanismGases.ANTIMATTER.get() && taken.getAmount() == antimatter[0],
                  "The reloaded port returned " + taken);
        });
    }

    /** Forge Energy equal to one mB of antimatter at the configured Joule rate. */
    private static long oneAntimatterFe() {
        int per = MekanismConfig.general.spsInputPerAntimatter.get();
        FloatingLong joules = MekanismConfig.general.spsEnergyPerInput.get().multiply(per);
        return EnergyUnit.FORGE_ENERGY.convertToAsLong(joules);
    }

    private static Sps placeFormed(GameTestHelper helper, BlockPos origin) {
        return place(helper, origin, null, COIL_PORT, IDLE_PORT, COIL);
    }

    private static Sps placeHoled(GameTestHelper helper, BlockPos origin) {
        return place(helper, origin, HOLE, HOLED_PORT, null, null).withHoledPort(origin.offset(HOLED_PORT));
    }

    private static Sps placeDisconnectedCoil(GameTestHelper helper, BlockPos origin) {
        Sps sps = place(helper, origin, null, null, null, new BlockPos(3, 3, 3));
        helper.setBlock(sps.coil(), MekanismBlocks.SUPERCHARGED_COIL.getBlock());
        return sps;
    }

    private static Sps place(GameTestHelper helper, BlockPos origin, @Nullable BlockPos hole, @Nullable BlockPos coilPort,
          @Nullable BlockPos idlePort, @Nullable BlockPos coil) {
        List<BlockPos> shell = new ArrayList<>();
        BlockPos casing = null;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Requirement requirement = requirement(x, y, z);
                    BlockPos local = new BlockPos(x, y, z);
                    if (local.equals(hole)) {
                        check(requirement == Requirement.FRAME || requirement == Requirement.OTHER, "The hole at " + local + " is not shell");
                        continue;
                    }
                    if (requirement == Requirement.IGNORED || requirement == Requirement.INNER) {
                        continue;
                    }
                    Block block = casingBlock();
                    if (local.equals(coilPort) || local.equals(idlePort)) {
                        check(requirement == Requirement.OTHER, local + " cannot hold a port");
                        block = portBlock();
                    } else if (requirement == Requirement.FRAME && casing == null) {
                        casing = origin.offset(local);
                    }
                    BlockPos pos = origin.offset(local);
                    helper.setBlock(pos, block);
                    shell.add(pos);
                }
            }
        }
        check(casing != null, "The shell has no frame");
        if (coil != null && coilPort != null) {
            helper.setBlock(origin.offset(coil), MekanismBlocks.SUPERCHARGED_COIL.getBlock());
        }
        return new Sps(origin, List.copyOf(shell), casing, coilPort == null ? null : origin.offset(coilPort),
              coilPort == null ? null : outward(coilPort), idlePort == null ? null : origin.offset(idlePort),
              idlePort == null ? null : outward(idlePort), coil == null ? null : origin.offset(coil), null, null);
    }

    /**
     * The same choice {@code SPSValidator} makes. X is preferred over Y, and Y over Z, which is how a shared edge
     * picks the face whose grid it reads.
     */
    private static Requirement requirement(int x, int y, int z) {
        boolean xWall = x == 0 || x == SIZE - 1;
        boolean yWall = y == 0 || y == SIZE - 1;
        boolean zWall = z == 0 || z == SIZE - 1;
        if (!xWall && !yWall && !zWall) {
            return Requirement.INNER;
        }
        int horizontal;
        int vertical;
        if (xWall) {
            horizontal = z;
            vertical = y;
        } else if (yWall) {
            horizontal = x;
            vertical = z;
        } else {
            horizontal = x;
            vertical = y;
        }
        return Requirement.values()[GRID[horizontal][vertical]];
    }

    private static Direction outward(BlockPos local) {
        if (local.getX() == 0) {
            return Direction.WEST;
        } else if (local.getX() == SIZE - 1) {
            return Direction.EAST;
        } else if (local.getY() == 0) {
            return Direction.DOWN;
        } else if (local.getY() == SIZE - 1) {
            return Direction.UP;
        } else if (local.getZ() == 0) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }

    /** The shell has finished merging, stayed unformed, and was not absorbed by a neighbour. */
    private static void separate(GameTestHelper helper, BlockPos pos, int blocks, String name) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntitySPSCasing, name + " has no SPS block");
        TileEntitySPSCasing tile = (TileEntitySPSCasing) blockEntity;
        int size = tile.getStructure().size();
        if (size < blocks) {
            throw new GameTestAssertException(name + " is still merging (" + size + " of " + blocks + ")");
        }
        check(!tile.getMultiblock().isFormed(), name + " formed");
        check(size == blocks, name + " shares a structure of " + size + " blocks");
    }

    private static Block casingBlock() {
        return MekanismBlocks.SPS_CASING.getBlock();
    }

    private static Block portBlock() {
        return MekanismBlocks.SPS_PORT.getBlock();
    }

    private static SavedBlock save(GameTestHelper helper, BlockPos pos) {
        return new SavedBlock(pos, helper.getBlockState(pos), helper.getBlockEntity(pos).saveWithFullMetadata());
    }

    private static TileEntitySPSPort port(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntitySPSPort, "The port is " + blockEntity);
        return (TileEntitySPSPort) blockEntity;
    }

    private static SPSMultiblockData formed(TileEntitySPSCasing tile) {
        SPSMultiblockData data = tile.getMultiblock();
        check(data.isFormed(), "The SPS is not formed");
        return data;
    }

    private static TileEntitySPSCasing master(GameTestHelper helper, Sps sps) {
        TileEntitySPSCasing master = null;
        for (BlockPos pos : sps.shell()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntitySPSCasing casing && casing.isMaster()) {
                check(master == null, "The SPS has two masters");
                master = casing;
            }
        }
        check(master != null, "The formed SPS has no master");
        return master;
    }

    private enum Requirement {
        IGNORED,
        FRAME,
        OTHER,
        INNER
    }

    private record Sps(BlockPos origin, List<BlockPos> shell, BlockPos casing, @Nullable BlockPos coilPort, @Nullable Direction coilSide,
                       @Nullable BlockPos idlePort, @Nullable Direction idleSide, @Nullable BlockPos coil, @Nullable BlockPos holedPort,
                       @Nullable Direction holedSide) {

        private Sps withHoledPort(BlockPos port) {
            return new Sps(origin, shell, casing, coilPort, coilSide, idlePort, idleSide, coil, port, outward(HOLED_PORT));
        }
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}
