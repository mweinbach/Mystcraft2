/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;
import static mekanism.gametest.CoreMachineGameTests.item;

import mekanism.api.Action;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.api.text.EnumColor;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.PigmentRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismPigments;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * One recipe for the nutritional liquifier, the pigment machines and the antiprotonic nucleosynthesizer. Pigments move
 * through the loader's pigment lookup. Paste stays at fifty millibuckets per food point: one dried kelp is one point.
 */
public class PigmentMachineGameTests {

    private static final long SAMPLE = 20_000;
    /** Two hundred ticks at 100,000 J, plus margin for the batch the nucleosynthesizer runs while the tank is full. */
    private static final long NUCLEOSYNTHESIZER_ENERGY = 30_000_000L;
    /** One nutrition point becomes this many millibuckets of nutritional paste. */
    private static final long PASTE_PER_FOOD = 50;

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void nutritionalLiquifierMakesPasteFromDriedKelp(GameTestHelper helper) {
        TileEntityMekanism liquifier = machine(helper, MekanismBlocks.NUTRITIONAL_LIQUIFIER);
        fillItem(liquifier, 0, new ItemStack(Items.DRIED_KELP));
        Fluid paste = MekanismFluids.NUTRITIONAL_PASTE.getStillFluid();
        Direction output = side(liquifier, RelativeSide.RIGHT);
        helper.succeedWhen(() -> {
            check(liquifier.getInventorySlots(null).get(0).isEmpty(), "The nutritional liquifier did not consume dried kelp");
            long stored = TestPlatform.extractBlockFluid(liquifier, output, paste, 1_000, false);
            check(stored == PASTE_PER_FOOD, "Dried kelp made " + stored + " mB of nutritional paste");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void pigmentExtractorExtractsRedWool(GameTestHelper helper) {
        TileEntityMekanism extractor = machine(helper, MekanismBlocks.PIGMENT_EXTRACTOR);
        fillItem(extractor, 0, new ItemStack(Items.RED_WOOL));
        Direction output = side(extractor, RelativeSide.RIGHT);
        Pigment red = pigment(EnumColor.RED);
        helper.succeedWhen(() -> {
            check(extractor.getInventorySlots(null).get(0).isEmpty(), "The pigment extractor did not consume red wool");
            expectPigment(extractor, output, red, 192);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void pigmentMixerMixesRedAndYellow(GameTestHelper helper) {
        TileEntityMekanism mixer = formedMixer(helper);
        Direction left = side(mixer, RelativeSide.LEFT);
        Direction right = side(mixer, RelativeSide.RIGHT);
        Direction front = side(mixer, RelativeSide.FRONT);
        fillPigment(mixer, left, pigment(EnumColor.RED), 1);
        fillPigment(mixer, right, pigment(EnumColor.YELLOW), 1);
        helper.succeedWhen(() -> expectPigment(mixer, front, pigment(EnumColor.ORANGE), 2));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void paintingMachinePaintsDyeBase(GameTestHelper helper) {
        TileEntityMekanism painter = machine(helper, MekanismBlocks.PAINTING_MACHINE);
        fillItem(painter, 1, new ItemStack(MekanismItems.DYE_BASE.get()));
        fillPigment(painter, side(painter, RelativeSide.BACK), pigment(EnumColor.RED), 256);
        helper.succeedWhen(() -> {
            check(painter.getInventorySlots(null).get(1).isEmpty(), "The painting machine did not consume dye base");
            expectItem(painter, 2, Items.RED_DYE, 1);
        });
    }

    /**
     * The iron recipe is two hundred ticks at 100,000 J. The machine also spends stored energy in a batch, so the tank
     * is filled past one tick before the tin and antimatter go in.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void nucleosynthesizerTurnsTinIntoIron(GameTestHelper helper) {
        TileEntityMekanism synthesizer = machine(helper, MekanismBlocks.ANTIPROTONIC_NUCLEOSYNTHESIZER);
        storeEnergy(synthesizer, NUCLEOSYNTHESIZER_ENERGY);
        fillItem(synthesizer, 1, new ItemStack(item("ingot_tin")));
        fillGas(synthesizer, side(synthesizer, RelativeSide.BACK), 1);
        helper.succeedWhen(() -> {
            check(synthesizer.getInventorySlots(null).get(1).isEmpty(), "The nucleosynthesizer did not consume the tin ingot");
            expectItem(synthesizer, 2, Items.IRON_INGOT, 1);
        });
    }

    private static TileEntityMekanism machine(GameTestHelper helper, BlockRegistryObject<?, ?> block) {
        TileEntityMekanism tile = CoreMachineGameTests.poweredMachine(helper, block);
        check(tile.getDirection() == Direction.NORTH, block.getInternalRegistryName() + " faces " + tile.getDirection());
        return tile;
    }

    /**
     * The mixer is a two-block machine. Place the bounding block above the controller before giving it energy, the same
     * way a player placement does.
     */
    private static TileEntityMekanism formedMixer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Block block = MekanismBlocks.PIGMENT_MIXER.getBlock();
        helper.setBlock(pos, block);
        ServerLevel level = helper.getLevel();
        BlockPos world = helper.absolutePos(pos);
        BlockState state = level.getBlockState(world);
        state.getBlock().setPlacedBy(level, world, state, null, new ItemStack(block));
        check(level.getBlockState(world.above()).is(MekanismBlocks.BOUNDING_BLOCK.getBlock()), "The pigment mixer has no bounding block");
        TileEntityMekanism mixer = (TileEntityMekanism) helper.getBlockEntity(pos);
        FloatingLong leftover = ((IStrictEnergyHandler) mixer).insertEnergy(FloatingLong.createConst(100_000), Action.EXECUTE);
        check(leftover.smallerThan(FloatingLong.createConst(100_000)), "The pigment mixer accepted no energy");
        check(mixer.getDirection() == Direction.NORTH, "The pigment mixer faces " + mixer.getDirection());
        return mixer;
    }

    private static void storeEnergy(TileEntityMekanism machine, long joules) {
        FloatingLong leftover = ((IStrictEnergyHandler) machine).insertEnergy(FloatingLong.createConst(joules), Action.EXECUTE);
        check(leftover.isZero(), machine.getBlockState().getBlock() + " left " + leftover + " J unstored");
    }

    private static Direction side(TileEntityMekanism tile, RelativeSide relative) {
        return relative.getDirection(tile.getDirection());
    }

    private static void fillItem(TileEntityMekanism machine, int slot, ItemStack stack) {
        IInventorySlot inventory = machine.getInventorySlots(null).get(slot);
        check(inventory.isItemValid(stack), machine.getBlockState().getBlock() + " slot " + slot + " rejects " + stack);
        inventory.setStack(stack);
    }

    private static void fillPigment(BlockEntity machine, Direction side, Pigment pigment, long amount) {
        long moved = TestPlatform.insertPigment(machine, side, pigment, amount, true);
        check(moved == amount, "Moved " + moved + " of " + amount + " " + pigment + " into " + side);
    }

    private static void fillGas(BlockEntity machine, Direction side, long amount) {
        long moved = TestPlatform.insertBlockGas(machine, side, MekanismGases.ANTIMATTER.get(), amount, true);
        check(moved == amount, "Moved " + moved + " of " + amount + " antimatter into " + side);
    }

    private static void expectPigment(BlockEntity machine, Direction side, Pigment pigment, long minimum) {
        PigmentStack stack = TestPlatform.extractPigment(machine, side, SAMPLE, false);
        check(stack.getType() == pigment && stack.getAmount() >= minimum,
              "Expected at least " + minimum + " " + pigment + " from " + side + ", found " + stack.getAmount() + " " + stack.getType());
    }

    private static void expectItem(TileEntityMekanism machine, int slot, Item item, int count) {
        ItemStack stack = machine.getInventorySlots(null).get(slot).getStack();
        check(stack.is(item) && stack.getCount() >= count, "Slot " + slot + " holds " + stack + ", expected " + count + " " + item);
    }

    private static Pigment pigment(EnumColor color) {
        PigmentRegistryObject<Pigment> registered = MekanismPigments.PIGMENT_COLOR_LOOKUP.get(color);
        check(registered != null, "No pigment is registered for " + color);
        return registered.get();
    }
}
