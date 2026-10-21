/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;
import static mekanism.gametest.CoreMachineGameTests.item;

import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registration.impl.SlurryRegistryObject;
import mekanism.common.registries.MekanismSlurries;
import mekanism.common.resource.PrimaryResource;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.machine.TileEntityRotaryCondensentrator;
import mekanism.common.tile.machine.TileEntitySolarNeutronActivator;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * One recipe for each gas-processing machine. Gases and slurries move through the loader's chemical lookups: Forge
 * capabilities on Forge and the Fabric API lookups on Fabric. The nutritional liquifier, pigment machines and
 * nucleosynthesizer are a separate recipe group and are not covered here. The electrolytic separator already is.
 */
public class ChemicalMachineGameTests {

    private static final long SAMPLE = 4_000;

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void chemicalInfuserCombinesHydrogenAndChlorine(GameTestHelper helper) {
        TileEntityMekanism infuser = machine(helper, MekanismBlocks.CHEMICAL_INFUSER);
        Direction left = side(infuser, RelativeSide.LEFT);
        Direction right = side(infuser, RelativeSide.RIGHT);
        Direction front = side(infuser, RelativeSide.FRONT);
        long refused = TestPlatform.insertGas(infuser, front, MekanismGases.HYDROGEN.get(), 10, true);
        check(refused == 0, "The infuser's output side accepted hydrogen");
        fillGas(infuser, left, MekanismGases.HYDROGEN.get(), 40);
        fillGas(infuser, right, MekanismGases.CHLORINE.get(), 40);
        helper.succeedWhen(() -> {
            expectGas(infuser, front, MekanismGases.HYDROGEN_CHLORIDE.get(), 1);
            GasStack leaked = TestPlatform.extractGas(infuser, left, SAMPLE, false);
            check(leaked.isEmpty(), "The infuser's input side gave gas back: " + leaked.getAmount() + " " + leaked.getType());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void oxidizerOxidizesSulfur(GameTestHelper helper) {
        TileEntityMekanism oxidizer = machine(helper, MekanismBlocks.CHEMICAL_OXIDIZER);
        fillItem(oxidizer, 0, new ItemStack(MekanismItems.SULFUR_DUST.get()));
        Direction output = side(oxidizer, RelativeSide.RIGHT);
        helper.succeedWhen(() -> {
            check(oxidizer.getInventorySlots(null).get(0).isEmpty(), "The oxidizer did not consume sulfur dust");
            expectGas(oxidizer, output, MekanismGases.SULFUR_DIOXIDE.get(), 100);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void dissolutionChamberDissolvesOsmiumOre(GameTestHelper helper) {
        TileEntityMekanism chamber = machine(helper, MekanismBlocks.CHEMICAL_DISSOLUTION_CHAMBER);
        fillItem(chamber, 1, new ItemStack(item("osmium_ore")));
        fillGas(chamber, side(chamber, RelativeSide.BACK), MekanismGases.SULFURIC_ACID.get(), 400);
        Slurry dirty = osmiumSlurry(true);
        helper.succeedWhen(() -> {
            check(chamber.getInventorySlots(null).get(1).isEmpty(), "The dissolution chamber did not consume osmium ore");
            expectSlurry(chamber, side(chamber, RelativeSide.RIGHT), dirty, 1_000);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void washerCleansDirtyOsmiumSlurry(GameTestHelper helper) {
        TileEntityMekanism washer = machine(helper, MekanismBlocks.CHEMICAL_WASHER);
        Direction input = side(washer, RelativeSide.BACK);
        fillFluid(washer, input, Fluids.WATER, 25);
        fillSlurry(washer, input, osmiumSlurry(true), 5);
        helper.succeedWhen(() -> expectSlurry(washer, side(washer, RelativeSide.RIGHT), osmiumSlurry(false), 1));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void crystallizerGrowsOsmiumCrystals(GameTestHelper helper) {
        TileEntityMekanism crystallizer = machine(helper, MekanismBlocks.CHEMICAL_CRYSTALLIZER);
        fillSlurry(crystallizer, side(crystallizer, RelativeSide.BACK), osmiumSlurry(false), 200);
        helper.succeedWhen(() -> expectItem(crystallizer, 1, item("crystal_osmium"), 1));
    }

    /**
     * Purification and injection draw a random amount of gas each tick. A full set of gas upgrades drops that mean
     * far enough that the 210-unit tank cannot run out before the operation finishes.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void purificationChamberPurifiesOsmiumOre(GameTestHelper helper) {
        TileEntityMekanism chamber = machine(helper, MekanismBlocks.PURIFICATION_CHAMBER);
        installGasUpgrades(chamber);
        fillItem(chamber, 0, new ItemStack(item("osmium_ore")));
        fillGas(chamber, side(chamber, RelativeSide.BACK), MekanismGases.OXYGEN.get(), 210);
        helper.succeedWhen(() -> {
            check(chamber.getInventorySlots(null).get(0).isEmpty(), "The purification chamber did not consume osmium ore");
            expectItem(chamber, 2, item("clump_osmium"), 3);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void injectionChamberTurnsDirtIntoMud(GameTestHelper helper) {
        TileEntityMekanism chamber = machine(helper, MekanismBlocks.CHEMICAL_INJECTION_CHAMBER);
        installGasUpgrades(chamber);
        fillItem(chamber, 0, new ItemStack(Items.DIRT));
        fillGas(chamber, side(chamber, RelativeSide.BACK), MekanismGases.WATER_VAPOR.get(), 210);
        helper.succeedWhen(() -> {
            check(chamber.getInventorySlots(null).get(0).isEmpty(), "The injection chamber did not consume dirt");
            expectItem(chamber, 2, Items.MUD, 1);
        });
    }

    /**
     * The condensentrator starts in condensentrating mode: gas in the back, liquid oxygen out the right.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void rotaryCondensentratorCondensesOxygen(GameTestHelper helper) {
        TileEntityMekanism condensentrator = machine(helper, MekanismBlocks.ROTARY_CONDENSENTRATOR);
        check(!((TileEntityRotaryCondensentrator) condensentrator).mode, "The condensentrator started decondensentrating");
        fillGas(condensentrator, side(condensentrator, RelativeSide.BACK), MekanismGases.OXYGEN.get(), 20);
        Fluid oxygen = MekanismFluids.OXYGEN.getStillFluid();
        Direction output = side(condensentrator, RelativeSide.RIGHT);
        helper.succeedWhen(() -> {
            long stored = TestPlatform.extractBlockFluid(condensentrator, output, oxygen, 100, false);
            check(stored >= 1, "The condensentrator stored " + stored + " mB of liquid oxygen");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void pressurizedReactionChamberMakesSubstrate(GameTestHelper helper) {
        TileEntityMekanism chamber = machine(helper, MekanismBlocks.PRESSURIZED_REACTION_CHAMBER);
        fillItem(chamber, 0, new ItemStack(MekanismItems.BIO_FUEL.get(), 2));
        fillFluid(chamber, side(chamber, RelativeSide.BACK), Fluids.WATER, 10);
        fillGas(chamber, side(chamber, RelativeSide.BACK), MekanismGases.HYDROGEN.get(), 100);
        helper.succeedWhen(() -> {
            expectItem(chamber, 1, MekanismItems.SUBSTRATE.get(), 1);
            expectGas(chamber, side(chamber, RelativeSide.RIGHT), MekanismGases.ETHENE.get(), 100);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void isotopicCentrifugeSeparatesFissileFuel(GameTestHelper helper) {
        TileEntityMekanism centrifuge = machine(helper, MekanismBlocks.ISOTOPIC_CENTRIFUGE);
        fillGas(centrifuge, side(centrifuge, RelativeSide.BACK), MekanismGases.URANIUM_HEXAFLUORIDE.get(), 8);
        helper.succeedWhen(() -> expectGas(centrifuge, side(centrifuge, RelativeSide.FRONT), MekanismGases.FISSILE_FUEL.get(), 1));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void solarNeutronActivatorMakesPolonium(GameTestHelper helper) {
        midday(helper);
        BlockPos pos = new BlockPos(1, 1, 1);
        Block block = MekanismBlocks.SOLAR_NEUTRON_ACTIVATOR.getBlock();
        helper.setBlock(pos, block);
        ServerLevel level = helper.getLevel();
        BlockPos world = helper.absolutePos(pos);
        BlockState state = level.getBlockState(world);
        state.getBlock().setPlacedBy(level, world, state, null, new ItemStack(block));
        // The panel is the bounding block on top of the controller. Clear above that panel.
        openSky(helper, 1, 1, 1, 1, 3);
        TileEntitySolarNeutronActivator activator = (TileEntitySolarNeutronActivator) helper.getBlockEntity(pos);
        check(activator.getDirection() == Direction.NORTH, "The solar neutron activator faces " + activator.getDirection());
        fillGas(activator, side(activator, RelativeSide.BACK), MekanismGases.NUCLEAR_WASTE.get(), 100);
        helper.succeedWhen(() -> {
            BlockPos panel = activator.getBlockPos().above();
            check(WorldUtils.canSeeSun(helper.getLevel(), panel),
                  "The solar neutron activator cannot see the sun at " + panel + " " + sky(helper, panel));
            expectGas(activator, side(activator, RelativeSide.FRONT), MekanismGases.POLONIUM.get(), 1);
        });
    }

    private static TileEntityMekanism machine(GameTestHelper helper, BlockRegistryObject<?, ?> block) {
        TileEntityMekanism tile = CoreMachineGameTests.poweredMachine(helper, block);
        check(tile.getDirection() == Direction.NORTH, block.getInternalRegistryName() + " faces " + tile.getDirection());
        return tile;
    }

    private static Direction side(TileEntityMekanism tile, RelativeSide relative) {
        return relative.getDirection(tile.getDirection());
    }

    private static void installGasUpgrades(TileEntityMekanism machine) {
        check(machine.supportsUpgrades(), machine.getBlockState().getBlock() + " has no upgrade component");
        int added = machine.getComponent().addUpgrades(Upgrade.GAS, Upgrade.GAS.getMax());
        check(added == Upgrade.GAS.getMax(), "Installed " + added + " gas upgrades");
    }

    private static void fillItem(TileEntityMekanism machine, int slot, ItemStack stack) {
        IInventorySlot inventory = machine.getInventorySlots(null).get(slot);
        check(inventory.isItemValid(stack), machine.getBlockState().getBlock() + " slot " + slot + " rejects " + stack);
        inventory.setStack(stack);
    }

    private static void fillGas(BlockEntity machine, Direction side, Gas gas, long amount) {
        long moved = TestPlatform.insertGas(machine, side, gas, amount, true);
        check(moved == amount, "Moved " + moved + " of " + amount + " " + gas + " into " + side);
    }

    private static void fillSlurry(BlockEntity machine, Direction side, Slurry slurry, long amount) {
        long moved = TestPlatform.insertSlurry(machine, side, slurry, amount, true);
        check(moved == amount, "Moved " + moved + " of " + amount + " " + slurry + " into " + side);
    }

    private static void fillFluid(BlockEntity machine, Direction side, Fluid fluid, long milliBuckets) {
        long moved = TestPlatform.insertBlockFluid(machine, side, fluid, milliBuckets, true);
        check(moved == milliBuckets, "Moved " + moved + " of " + milliBuckets + " mB of " + fluid + " into " + side);
    }

    private static void expectGas(BlockEntity machine, Direction side, Gas gas, long minimum) {
        GasStack stack = TestPlatform.extractGas(machine, side, SAMPLE, false);
        check(stack.getType() == gas && stack.getAmount() >= minimum,
              "Expected at least " + minimum + " " + gas + " from " + side + ", found " + stack.getAmount() + " " + stack.getType());
    }

    private static void expectSlurry(BlockEntity machine, Direction side, Slurry slurry, long minimum) {
        SlurryStack stack = TestPlatform.extractSlurry(machine, side, SAMPLE, false);
        check(stack.getType() == slurry && stack.getAmount() >= minimum,
              "Expected at least " + minimum + " " + slurry + " from " + side + ", found " + stack.getAmount() + " " + stack.getType());
    }

    private static void expectItem(TileEntityMekanism machine, int slot, Item item, int count) {
        ItemStack stack = machine.getInventorySlots(null).get(slot).getStack();
        check(stack.is(item) && stack.getCount() >= count, "Slot " + slot + " holds " + stack + ", expected " + count + " " + item);
    }

    private static Slurry osmiumSlurry(boolean dirty) {
        SlurryRegistryObject<Slurry, Slurry> registered = MekanismSlurries.PROCESSED_RESOURCES.get(PrimaryResource.OSMIUM);
        check(registered != null, "Osmium slurry is not registered");
        return dirty ? registered.getDirtySlurry() : registered.getCleanSlurry();
    }

    private static void midday(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(6_000);
        level.setWeatherParameters(10_000, 0, false, false);
    }

    /**
     * The game test world refills a cleared column with aquifer water, which keeps {@code canSeeSky} false. Barriers
     * around the column stop that flow. This is the same setup the solar generator test uses.
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
              + " rain=" + level.isRaining();
    }
}
