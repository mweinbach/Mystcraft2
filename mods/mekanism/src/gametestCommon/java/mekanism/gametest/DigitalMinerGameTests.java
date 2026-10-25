/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.Upgrade;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.content.miner.MinerItemStackFilter;
import mekanism.common.content.miner.MinerTagFilter;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The digital miner searches its radius, breaks blocks its filters accept, and puts the drops where the loader's item
 * API can see them. An anchor upgrade keeps the chunk ticket.
 */
public class DigitalMinerGameTests {

    private static final BlockPos MINER = new BlockPos(3, 2, 2);
    private static final BlockPos ORE = new BlockPos(3, 1, 2);
    private static final BlockPos OTHER = new BlockPos(4, 1, 2);

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void minerMinesMatchingOreIntoItsInventory(GameTestHelper helper) {
        TileEntityDigitalMiner miner = runningMiner(helper, itemFilter(Items.IRON_ORE));
        helper.setBlock(ORE, Blocks.IRON_ORE);
        helper.setBlock(OTHER, Blocks.COAL_ORE);
        BlockEntity port = outputPort(helper, miner);
        Direction side = miner.getOppositeDirection();
        miner.start();
        helper.succeedWhen(() -> {
            int rawIron = TestPlatform.countItems(port, side, Items.RAW_IRON);
            int coal = TestPlatform.countItems(port, side, Items.COAL);
            if (rawIron < 1 || coal != 0 || helper.getBlockState(ORE).is(Blocks.IRON_ORE) || !helper.getBlockState(OTHER).is(Blocks.COAL_ORE)) {
                throw new GameTestAssertException("Item filter should leave coal ore and put raw iron on the output port, found "
                      + rawIron + " raw iron and " + coal + " coal. " + describe(miner));
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void minerEjectsMatchingOreIntoAnAdjacentInventory(GameTestHelper helper) {
        TileEntityDigitalMiner miner = runningMiner(helper, itemFilter(Items.IRON_ORE));
        miner.toggleAutoEject();
        BlockPos chestPos = MINER.above().relative(miner.getOppositeDirection(), 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(ORE, Blocks.IRON_ORE);
        BlockEntity chest = helper.getBlockEntity(chestPos);
        Direction intoChest = miner.getDirection();
        miner.start();
        helper.succeedWhen(() -> {
            int ejected = TestPlatform.countItems(chest, intoChest, Items.RAW_IRON);
            int held = TestPlatform.countItems(outputPort(helper, miner), miner.getOppositeDirection(), Items.RAW_IRON);
            if (ejected < 1 || held != 0 || helper.getBlockState(ORE).is(Blocks.IRON_ORE)) {
                throw new GameTestAssertException("Auto-eject should move raw iron into the chest through the item API, chest="
                      + ejected + " miner=" + held + ". " + describe(miner));
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void tagFilterMinesOnlyBlocksInTheTag(GameTestHelper helper) {
        TileEntityDigitalMiner miner = runningMiner(helper, new MinerTagFilter("forge:ores/iron"));
        helper.setBlock(ORE, Blocks.IRON_ORE);
        helper.setBlock(OTHER, Blocks.COAL_ORE);
        BlockEntity port = outputPort(helper, miner);
        Direction side = miner.getOppositeDirection();
        miner.start();
        helper.succeedWhen(() -> {
            int rawIron = TestPlatform.countItems(port, side, Items.RAW_IRON);
            int coal = TestPlatform.countItems(port, side, Items.COAL);
            if (rawIron < 1 || coal != 0 || helper.getBlockState(ORE).is(Blocks.IRON_ORE) || !helper.getBlockState(OTHER).is(Blocks.COAL_ORE)) {
                throw new GameTestAssertException("forge:ores/iron should mine iron ore and leave coal ore, found "
                      + rawIron + " raw iron and " + coal + " coal. " + describe(miner));
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void replaceBlockReplacesTheMinedOre(GameTestHelper helper) {
        MinerItemStackFilter filter = itemFilter(Items.IRON_ORE);
        filter.replaceTarget = Items.COBBLESTONE;
        filter.requiresReplacement = true;
        TileEntityDigitalMiner miner = runningMiner(helper, filter);
        BlockEntity input = helper.getBlockEntity(MINER.above());
        long inserted = TestPlatform.insertItems(input, Direction.UP, new ItemStack(Items.COBBLESTONE));
        check(inserted == 1, "The input port did not accept the replacement cobblestone");
        helper.setBlock(ORE, Blocks.IRON_ORE);
        BlockEntity port = outputPort(helper, miner);
        Direction side = miner.getOppositeDirection();
        miner.start();
        helper.succeedWhen(() -> {
            int rawIron = TestPlatform.countItems(port, side, Items.RAW_IRON);
            int cobble = TestPlatform.countItems(port, side, Items.COBBLESTONE);
            if (rawIron < 1 || cobble != 0 || !helper.getBlockState(ORE).is(Blocks.COBBLESTONE)) {
                throw new GameTestAssertException("The mined iron ore should be cobblestone and the drop should be raw iron, found "
                      + helper.getBlockState(ORE).getBlock() + ", " + rawIron + " raw iron, " + cobble + " cobblestone. " + describe(miner));
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void silkTouchYieldsTheOreBlock(GameTestHelper helper) {
        TileEntityDigitalMiner miner = runningMiner(helper, itemFilter(Items.IRON_ORE));
        miner.toggleSilkTouch();
        fillEnergy(miner);
        helper.setBlock(ORE, Blocks.IRON_ORE);
        BlockEntity port = outputPort(helper, miner);
        Direction side = miner.getOppositeDirection();
        miner.start();
        helper.succeedWhen(() -> {
            int ore = TestPlatform.countItems(port, side, Items.IRON_ORE);
            int rawIron = TestPlatform.countItems(port, side, Items.RAW_IRON);
            if (ore < 1 || rawIron != 0 || helper.getBlockState(ORE).is(Blocks.IRON_ORE)) {
                throw new GameTestAssertException("Silk touch should yield the iron ore block, found " + ore + " ore and "
                      + rawIron + " raw iron. " + describe(miner));
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    @LevelTickingOnly
    public static void anchorUpgradeKeepsTheMinerChunkLoaded(GameTestHelper helper) {
        TileEntityDigitalMiner miner = placedMiner(helper);
        check(miner.getComponent().supports(Upgrade.ANCHOR), "The digital miner does not support the anchor upgrade");
        check(miner.getComponent().addUpgrades(Upgrade.ANCHOR, 1) == 1, "The anchor upgrade was not installed");
        BlockPos absolute = miner.getBlockPos();
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(absolute);
        boolean[] removed = {false};
        helper.succeedWhen(() -> {
            if (!removed[0]) {
                check(TestPlatform.anchorChunkLoaded(level, absolute, chunk),
                      "Anchor upgrade did not keep the miner chunk loaded: " + ChunkLoadingGameTests.chunkTicketDebug(level, chunk));
                String restored = TestPlatform.anchorChunkRestored(level, absolute, chunk);
                check(restored == null, restored);
                helper.destroyBlock(MINER);
                removed[0] = true;
                throw new GameTestAssertException("Waiting for the miner chunk ticket to drop");
            }
            check(!TestPlatform.anchorChunkLoaded(level, absolute, chunk),
                  "Chunk ticket remained after the miner was removed: " + ChunkLoadingGameTests.chunkTicketDebug(level, chunk));
        });
    }

    @GameTest(template = TEMPLATE)
    public static void areaPreviewRenderBoxCoversTheMiningArea(GameTestHelper helper) {
        TileEntityDigitalMiner miner = placedMiner(helper);
        int y = miner.getBlockPos().getY();
        miner.setRadiusFromPacket(2);
        setVerticalRange(miner, y, y + 3);
        BlockPos world = miner.getBlockPos();
        AABB miningArea = new AABB(world.getX() - miner.getRadius(), miner.getMinY(), world.getZ() - miner.getRadius(),
              world.getX() + miner.getRadius() + 1, miner.getMaxY() + 1, world.getZ() + miner.getRadius() + 1);
        AABB idle = TestPlatform.renderBounds(miner);
        check(!idle.equals(miningArea), "The miner render box is the mining area while the preview is off: " + idle);
        miner.toggleClientRendering();
        AABB preview = TestPlatform.renderBounds(miner);
        check(preview.equals(miningArea), "Area preview render box " + preview + " != " + miningArea);
        miner.toggleClientRendering();
        check(!TestPlatform.renderBounds(miner).equals(miningArea), "The area preview stayed on after it was toggled off");
        helper.succeed();
    }

    private static MinerItemStackFilter itemFilter(Item item) {
        return new MinerItemStackFilter(new ItemStack(item));
    }

    private static TileEntityDigitalMiner runningMiner(GameTestHelper helper, MinerFilter<?> filter) {
        TileEntityDigitalMiner miner = placedMiner(helper);
        miner.setRadiusFromPacket(1);
        int y = helper.absolutePos(ORE).getY();
        setVerticalRange(miner, y, y);
        check(miner.getFilterManager().addFilter(filter), "The miner rejected " + filter.getFilterType());
        fillEnergy(miner);
        return miner;
    }

    private static TileEntityDigitalMiner placedMiner(GameTestHelper helper) {
        Block block = MekanismBlocks.DIGITAL_MINER.getBlock();
        helper.setBlock(MINER, block);
        ServerLevel level = helper.getLevel();
        BlockPos world = helper.absolutePos(MINER);
        BlockState state = level.getBlockState(world);
        state.getBlock().setPlacedBy(level, world, state, null, new ItemStack(block));
        BlockEntity blockEntity = helper.getBlockEntity(MINER);
        check(blockEntity instanceof TileEntityDigitalMiner, "The digital miner has no tile: " + blockEntity);
        TileEntityDigitalMiner miner = (TileEntityDigitalMiner) blockEntity;
        check(miner.getDirection() == Direction.NORTH, "The digital miner faces " + miner.getDirection());
        check(helper.getBlockState(MINER.above()).is(MekanismBlocks.BOUNDING_BLOCK.getBlock()), "The digital miner has no bounding block above it");
        check(helper.getBlockState(MINER.above().south()).is(MekanismBlocks.BOUNDING_BLOCK.getBlock()), "The digital miner has no back output port");
        return miner;
    }

    private static void setVerticalRange(TileEntityDigitalMiner miner, int minY, int maxY) {
        if (maxY > miner.getMaxY()) {
            miner.setMaxYFromPacket(maxY);
            miner.setMinYFromPacket(minY);
        } else {
            miner.setMinYFromPacket(minY);
            miner.setMaxYFromPacket(maxY);
        }
        check(miner.getMinY() == minY && miner.getMaxY() == maxY, "Vertical range is " + miner.getMinY() + ".." + miner.getMaxY()
              + ", expected " + minY + ".." + maxY);
    }

    private static void fillEnergy(TileEntityDigitalMiner miner) {
        FloatingLong capacity = miner.getEnergyContainer().getMaxEnergy();
        FloatingLong leftover = ((IStrictEnergyHandler) miner).insertEnergy(capacity, Action.EXECUTE);
        check(miner.getEnergyContainer().getEnergy().greaterOrEqual(miner.getEnergyContainer().getEnergyPerTick()),
              "The miner does not have a tick of energy: stored " + miner.getEnergyContainer().getEnergy() + ", cost "
                    + miner.getEnergyContainer().getEnergyPerTick() + ", rejected " + leftover);
    }

    private static BlockEntity outputPort(GameTestHelper helper, TileEntityDigitalMiner miner) {
        return helper.getBlockEntity(MINER.above().relative(miner.getOppositeDirection()));
    }

    private static String describe(TileEntityDigitalMiner miner) {
        StringBuilder slots = new StringBuilder();
        List<IInventorySlot> inventory = miner.getInventorySlots(null);
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i).getStack();
            if (!stack.isEmpty()) {
                if (!slots.isEmpty()) {
                    slots.append(", ");
                }
                slots.append(i).append('=').append(stack.getCount()).append('x').append(stack.getItem());
            }
        }
        return "state=" + miner.searcher.state + " running=" + miner.isRunning() + " toMine=" + miner.getToMine()
              + " radius=" + miner.getRadius() + " y=" + miner.getMinY() + ".." + miner.getMaxY()
              + " energy=" + miner.getEnergyContainer().getEnergy() + " slots=" + (slots.isEmpty() ? "empty" : slots);
    }
}
