/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Mekanism's machines and other blocks' storages meet through the loader's energy and item APIs: Forge Energy and
 * capabilities on Forge, Team Reborn Energy and the Transfer API on Fabric.
 */
public class LoaderInteropGameTests {

    @GameTest(template = TEMPLATE)
    public static void machinesTakeEnergyThroughTheLoaderApi(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.ENRICHMENT_CHAMBER.getBlock());
        BlockEntity chamber = helper.getBlockEntity(pos);
        long accepted = 0;
        for (Direction side : Direction.values()) {
            accepted += TestPlatform.insertEnergy(chamber, side, 1_000);
        }
        check(accepted > 0, "The enrichment chamber took no energy through the loader's energy API");
        check(!((IStrictEnergyHandler) chamber).getEnergy(0).isZero(), "The energy the loader's API reported moved is not stored");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void machinesTakeItemsThroughTheLoaderApi(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.CRUSHER.getBlock());
        TileEntityMekanism crusher = (TileEntityMekanism) helper.getBlockEntity(pos);
        long accepted = 0;
        for (Direction side : Direction.values()) {
            accepted += TestPlatform.insertItems(crusher, side, new ItemStack(Items.IRON_INGOT, 4));
        }
        IInventorySlot input = crusher.getInventorySlots(null).get(0);
        check(accepted > 0, "The crusher took no items through the loader's item API");
        check(input.getStack().is(Items.IRON_INGOT) && input.getCount() == accepted,
              "The crusher's input holds " + input.getStack() + " after the loader's API moved " + accepted + " iron ingots");
        helper.succeed();
    }

    /**
     * Mekanism reaches other blocks' inventories through Forge's item handler capability; a chest has one on both loaders.
     */
    @GameTest(template = TEMPLATE)
    public static void foreignInventoriesAnswerTheItemCapability(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity chest = helper.getBlockEntity(pos);
        int moved = TestPlatform.insertThroughItemCapability(chest, Direction.UP, new ItemStack(Items.IRON_INGOT, 5));
        check(moved == 5, "Inserted " + moved + " of 5 iron ingots through the chest's item capability");
        check(((Container) chest).countItem(Items.IRON_INGOT) == 5, "The chest does not hold the inserted iron ingots");
        helper.succeed();
    }
}
