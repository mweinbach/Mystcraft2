/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.api.math.FloatingLong;
import mekanism.common.registries.MekanismGases;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.registries.GeneratorsFluids;
import mekanism.generators.common.tile.TileEntityAdvancedSolarGenerator;
import mekanism.generators.common.tile.TileEntityBioGenerator;
import mekanism.generators.common.tile.TileEntityGasGenerator;
import mekanism.generators.common.tile.TileEntityGenerator;
import mekanism.generators.common.tile.TileEntityHeatGenerator;
import mekanism.generators.common.tile.TileEntitySolarGenerator;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;

/**
 * Heat, solar, advanced solar, gas-burning, bio and wind generators produce energy, and that energy leaves through
 * the loader's energy API: Forge Energy on Forge and Team Reborn Energy on Fabric.
 */
public class SimpleGeneratorGameTests {

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void solarGeneratorProducesInDaylight(GameTestHelper helper) {
        midday(helper);
        BlockPos pos = new BlockPos(1, 1, 1);
        place(helper, pos, GeneratorsBlocks.SOLAR_GENERATOR.getBlock());
        openSky(helper, 1, 1, 1, 1, 2);
        TileEntitySolarGenerator solar = generator(helper, pos, TileEntitySolarGenerator.class);
        helper.succeedWhen(() -> {
            if (!solar.canSeeSun()) {
                BlockPos world = solar.getBlockPos();
                throw new GameTestAssertException("Solar generator cannot see the sun at " + world + " " + sky(helper, world));
            }
            emits(solar, Direction.DOWN);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void advancedSolarGeneratorProducesInDaylight(GameTestHelper helper) {
        midday(helper);
        BlockPos pos = new BlockPos(4, 1, 4);
        place(helper, pos, GeneratorsBlocks.ADVANCED_SOLAR_GENERATOR.getBlock());
        openSky(helper, 3, 3, 5, 5, 4);
        TileEntityAdvancedSolarGenerator solar = generator(helper, pos, TileEntityAdvancedSolarGenerator.class);
        helper.succeedWhen(() -> {
            if (!solar.canSeeSun()) {
                throw new GameTestAssertException("Advanced solar generator cannot see the sun at " + solar.getBlockPos()
                      + " " + sky(helper, solar.getBlockPos().above(2)));
            }
            emits(solar, solar.getDirection());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gasGeneratorBurnsHydrogen(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, GeneratorsBlocks.GAS_BURNING_GENERATOR.getBlock());
        TileEntityGasGenerator gas = generator(helper, pos, TileEntityGasGenerator.class);
        IGasTank fuel = gas.fuelTank;
        GasStack remainder = fuel.insert(new GasStack(MekanismGases.HYDROGEN.get(), 200), Action.EXECUTE, AutomationType.INTERNAL);
        check(remainder.isEmpty(), "The gas-burning generator refused hydrogen: " + remainder);
        long fueled = fuel.getStored();
        helper.succeedWhen(() -> {
            check(fuel.getStored() < fueled, "The gas-burning generator did not burn hydrogen");
            emits(gas, gas.getDirection());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void heatGeneratorBurnsLava(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, GeneratorsBlocks.HEAT_GENERATOR.getBlock());
        TileEntityHeatGenerator heat = generator(helper, pos, TileEntityHeatGenerator.class);
        FluidStack remainder = heat.lavaTank.insert(new FluidStack(Fluids.LAVA, 100), Action.EXECUTE, AutomationType.INTERNAL);
        check(remainder.isEmpty(), "The heat generator refused lava: " + remainder);
        int fueled = heat.lavaTank.getFluidAmount();
        helper.succeedWhen(() -> {
            check(heat.lavaTank.getFluidAmount() < fueled, "The heat generator did not burn lava");
            emits(heat, heat.getDirection());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void bioGeneratorBurnsBioethanol(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, GeneratorsBlocks.BIO_GENERATOR.getBlock());
        TileEntityBioGenerator bio = generator(helper, pos, TileEntityBioGenerator.class);
        FluidStack remainder = bio.bioFuelTank.insert(new FluidStack(GeneratorsFluids.BIOETHANOL.getStillFluid(), 20), Action.EXECUTE, AutomationType.INTERNAL);
        check(remainder.isEmpty(), "The bio generator refused bioethanol: " + remainder);
        int fueled = bio.bioFuelTank.getFluidAmount();
        helper.succeedWhen(() -> {
            check(bio.bioFuelTank.getFluidAmount() < fueled, "The bio generator did not burn bioethanol");
            emits(bio, bio.getDirection());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void windGeneratorProducesInOpenSky(GameTestHelper helper) {
        midday(helper);
        BlockPos pos = new BlockPos(1, 1, 1);
        place(helper, pos, GeneratorsBlocks.WIND_GENERATOR.getBlock());
        openSky(helper, 1, 1, 1, 1, 6);
        TileEntityWindGenerator wind = generator(helper, pos, TileEntityWindGenerator.class);
        helper.succeedWhen(() -> {
            if (wind.getEnergyContainer().getEnergy().isZero()) {
                BlockPos top = wind.getBlockPos().above(4);
                throw new GameTestAssertException("Wind generator stored no energy; top=" + top
                      + " block=" + helper.getLevel().getBlockState(top).getBlock()
                      + " " + sky(helper, top)
                      + " blacklist=" + wind.isBlacklistDimension());
            }
            emits(wind, wind.getDirection());
        });
    }

    /**
     * The rotor is drawn well outside the block. The frustum test uses this box, on Forge directly and on Fabric
     * through the block entity's render bounds.
     */
    @GameTest(template = TEMPLATE)
    public static void windGeneratorRotorUsesExtendedRenderBox(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, GeneratorsBlocks.WIND_GENERATOR.getBlock());
        TileEntityWindGenerator wind = generator(helper, pos, TileEntityWindGenerator.class);
        BlockPos world = wind.getBlockPos();
        AABB expected = new AABB(world.offset(-2, 0, -2), world.offset(3, 7, 3));
        AABB actual = TestPlatform.renderBounds(wind);
        check(actual.equals(expected), "Wind generator render box " + actual + " != " + expected);
        helper.succeed();
    }

    /**
     * Stored energy leaves through the generator's output side and stays put on a side that is not an output.
     * {@link TestPlatform#extractEnergy} is Forge Energy or Team Reborn Energy, depending on the loader.
     */
    private static void emits(TileEntityGenerator generator, Direction output) {
        check(generator.getDirection() == Direction.NORTH, generator.getBlockState().getBlock() + " faces " + generator.getDirection());
        // getEnergy() is the live container value. Extraction mutates it, so the before/after comparison needs a snapshot.
        FloatingLong stored = generator.getEnergyContainer().getEnergy().copy();
        check(stored.greaterThan(FloatingLong.ZERO), generator.getBlockState().getBlock() + " stored no energy");
        long refused = TestPlatform.extractEnergy(generator, Direction.UP, 100_000);
        check(refused == 0, generator.getBlockState().getBlock() + " gave " + refused + " energy out the top");
        check(generator.getEnergyContainer().getEnergy().equals(stored), "A refused extraction changed stored energy");
        long extracted = TestPlatform.extractEnergy(generator, output, 100_000);
        check(extracted > 0, generator.getBlockState().getBlock() + " output " + output + " gave no energy through the loader API; stored " + stored);
        check(generator.getEnergyContainer().getEnergy().smallerThan(stored), "Extracted energy is still stored");
    }

    private static <T extends TileEntityMekanism> T generator(GameTestHelper helper, BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(type.isInstance(blockEntity), "Expected " + type.getSimpleName() + ", found " + blockEntity);
        return type.cast(blockEntity);
    }

    /**
     * {@code setBlock} does not run {@link Block#setPlacedBy}, which is where generators with a rotor or panel
     * attach their bounding blocks. A player placement does both.
     */
    private static void place(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        ServerLevel level = helper.getLevel();
        BlockPos world = helper.absolutePos(pos);
        BlockState state = level.getBlockState(world);
        state.getBlock().setPlacedBy(level, world, state, null, new ItemStack(block));
        primeSkyLight(level, world);
    }

    private static void midday(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(6_000);
        level.setWeatherParameters(10_000, 0, false, false);
    }

    /**
     * Clears every column in the rectangle and walls the outside with barriers up through the highest
     * non-air block. The game test world is the overworld, so aquifer water refills a cleared column
     * and keeps {@code canSeeSky} false. Barriers stop that flow without sitting in the sky column.
     */
    private static void openSky(GameTestHelper helper, int minX, int minZ, int maxX, int maxZ, int fromY) {
        ServerLevel level = helper.getLevel();
        int startY = helper.absolutePos(new BlockPos(minX, fromY, minZ)).getY();
        int highest = startY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                BlockPos column = helper.absolutePos(new BlockPos(x, fromY, z));
                for (int y = startY; y < level.getMaxBuildHeight(); y++) {
                    cursor.set(column.getX(), y, column.getZ());
                    if (!level.getBlockState(cursor).isAir()) {
                        highest = Math.max(highest, y);
                    }
                }
            }
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                boolean inside = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                BlockPos column = helper.absolutePos(new BlockPos(x, fromY, z));
                for (int y = startY; y <= highest; y++) {
                    cursor.set(column.getX(), y, column.getZ());
                    BlockState state = level.getBlockState(cursor);
                    if (inside) {
                        if (!state.isAir()) {
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    } else if (!state.is(Blocks.BARRIER)) {
                        level.setBlock(cursor, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        primeSkyLight(level, helper.absolutePos(new BlockPos(minX, fromY, minZ)));
    }

    /**
     * GameTest chunks are often still unlit when the test starts, so {@code canSeeSky} stays false. Ask the light
     * engine to finish the chunk and give it a larger batch than the default of five tasks.
     */
    private static void primeSkyLight(ServerLevel level, BlockPos world) {
        ThreadedLevelLightEngine engine = level.getChunkSource().getLightEngine();
        engine.setTaskPerBatch(512);
        ChunkAccess chunk = level.getChunkAt(world);
        if (!chunk.isLightCorrect()) {
            engine.lightChunk(chunk, true);
        }
        engine.checkBlock(world);
    }

    private static String sky(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        return "time=" + level.getDayTime() + " skyDarken=" + level.getSkyDarken() + " canSeeSky=" + level.canSeeSky(pos)
              + " skyLight=" + level.getBrightness(LightLayer.SKY, pos)
              + " lightCorrect=" + level.getChunkAt(pos).isLightCorrect()
              + " blocker=" + firstBlocker(level, pos)
              + " rain=" + level.isRaining();
    }

    private static String firstBlocker(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
            cursor.set(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir()) {
                return state.getBlock() + "@" + y;
            }
        }
        return "none";
    }
}
