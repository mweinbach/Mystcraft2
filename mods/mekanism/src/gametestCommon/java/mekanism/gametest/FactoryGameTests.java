/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.factory.TileEntityCombiningFactory;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.factory.TileEntityItemStackGasToItemStackFactory;
import mekanism.common.tile.factory.TileEntityItemStackToItemStackFactory;
import mekanism.common.tile.factory.TileEntityMetallurgicInfuserFactory;
import mekanism.common.tile.factory.TileEntitySawingFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Factories run one recipe per process at the same time, auto-sort balances identical inputs, and a tier installer
 * keeps the contents when it replaces the block.
 */
public class FactoryGameTests {

    @GameTest(template = TEMPLATE)
    public static void everyFactoryTierAndTypePlacesItsTile(GameTestHelper helper) {
        int index = 0;
        for (FactoryTier tier : FactoryTier.values()) {
            for (FactoryType type : FactoryType.values()) {
                BlockPos pos = new BlockPos(index % 6, 1, index / 6);
                TileEntityFactory<?> factory = place(helper, pos, tier, type);
                check(factory.tier == tier, type + " " + tier + " reported tier " + factory.tier);
                check(factory.getFactoryType() == type, tier + " factory reported type " + factory.getFactoryType());
                check(factory.tier.processes == tier.processes, type + " " + tier + " has " + factory.tier.processes + " processes");
                check(tileClass(type).isInstance(factory), type + " placed " + factory.getClass().getSimpleName());
                check(!factory.getEnergyContainers(null).isEmpty(), type + " " + tier + " has no energy container");
                Block block = MekanismBlocks.getFactory(tier, type).getBlock();
                AttributeUpgradeable upgradeable = Attribute.get(block, AttributeUpgradeable.class);
                check(upgradeable != null, type + " " + tier + " is missing its upgrade attribute");
                if (tier != FactoryTier.ULTIMATE) {
                    FactoryTier next = FactoryTier.values()[tier.ordinal() + 1];
                    BlockState upgraded = upgradeable.upgradeResult(helper.getBlockState(pos), next.getBaseTier());
                    Block expected = MekanismBlocks.getFactory(next, type).getBlock();
                    check(upgraded.is(expected), type + " " + tier + " upgrades to " + upgraded.getBlock());
                }
                index++;
            }
        }
        check(index == FactoryTier.values().length * FactoryType.values().length, "Placed " + index + " factories");
        helper.succeed();
    }

    /**
     * A basic smelting factory has three processes and one operation's worth of energy. Each slot smelts once together;
     * one slot doing all the work would empty that slot and leave the others untouched.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void basicSmeltingFactoryProcessesStacksInParallel(GameTestHelper helper) {
        TileEntityFactory<?> factory = place(helper, new BlockPos(1, 1, 1), FactoryTier.BASIC, FactoryType.SMELTING);
        check(factory.tier.processes == 3, "Basic factory processes: " + factory.tier.processes);
        List<IInventorySlot> slots = factory.getInventorySlots(null);
        check(slots.size() == 7, "Basic smelting factory slots: " + slots.size());
        fillEnergy(factory);
        for (int process = 0; process < 3; process++) {
            IInventorySlot input = inputSlot(slots, process);
            check(input.isItemValid(new ItemStack(Items.RAW_IRON)), "Process " + process + " rejects raw iron");
            input.setStack(new ItemStack(Items.RAW_IRON, 2));
        }
        helper.succeedWhen(() -> {
            for (int process = 0; process < 3; process++) {
                ItemStack input = inputSlot(slots, process).getStack();
                ItemStack output = outputSlot(slots, process).getStack();
                if (!input.is(Items.RAW_IRON) || input.getCount() != 1 || !output.is(Items.IRON_INGOT) || output.getCount() != 1) {
                    throw new GameTestAssertException("Process " + process + " input " + input + " output " + output);
                }
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void autoSortSpreadsOneStackAcrossProcesses(GameTestHelper helper) {
        TileEntityFactory<?> factory = place(helper, new BlockPos(1, 1, 1), FactoryTier.BASIC, FactoryType.SMELTING);
        check(!factory.isSorting(), "Auto-sort starts enabled");
        factory.toggleSorting();
        check(factory.isSorting(), "Auto-sort did not enable");
        List<IInventorySlot> slots = factory.getInventorySlots(null);
        inputSlot(slots, 0).setStack(new ItemStack(Items.RAW_IRON, 6));
        helper.succeedWhen(() -> {
            for (int process = 0; process < 3; process++) {
                ItemStack input = inputSlot(slots, process).getStack();
                if (!input.is(Items.RAW_IRON) || input.getCount() != 2) {
                    throw new GameTestAssertException("Process " + process + " after auto-sort: " + input);
                }
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void tierInstallerKeepsItemsAndEnergy(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        TileEntityFactory<?> factory = place(helper, pos, FactoryTier.BASIC, FactoryType.SMELTING);
        FloatingLong stored = FloatingLong.createConst(12_345);
        FloatingLong leftover = ((IStrictEnergyHandler) factory).insertEnergy(stored, Action.EXECUTE);
        check(leftover.isZero(), "Factory stored " + stored.subtract(leftover) + " of " + stored);
        List<IInventorySlot> slots = factory.getInventorySlots(null);
        inputSlot(slots, 0).setStack(new ItemStack(Items.RAW_IRON, 5));
        outputSlot(slots, 0).setStack(new ItemStack(Items.IRON_INGOT, 2));
        inputSlot(slots, 1).setStack(new ItemStack(Items.RAW_COPPER, 3));
        factory.toggleSorting();

        Player player = helper.makeMockPlayer();
        ItemStack installer = new ItemStack(MekanismItems.ADVANCED_TIER_INSTALLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, installer);
        BlockPos absolute = helper.absolutePos(pos);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        InteractionResult result = installer.getItem().useOn(context);
        check(result.consumesAction(), "Advanced tier installer returned " + result);

        Block advanced = MekanismBlocks.getFactory(FactoryTier.ADVANCED, FactoryType.SMELTING).getBlock();
        check(helper.getBlockState(pos).is(advanced), "Block after install: " + helper.getBlockState(pos));
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityFactory, "Upgraded block entity: " + blockEntity);
        TileEntityFactory<?> upgraded = (TileEntityFactory<?>) blockEntity;
        check(upgraded.tier == FactoryTier.ADVANCED && upgraded.tier.processes == 5, "Upgraded tier " + upgraded.tier);
        check(upgraded.isSorting(), "Auto-sort was cleared by the tier installer");
        FloatingLong energy = energy(upgraded);
        check(energy.equals(stored), "Energy after upgrade is " + energy);
        List<IInventorySlot> upgradedSlots = upgraded.getInventorySlots(null);
        check(stackIs(inputSlot(upgradedSlots, 0).getStack(), Items.RAW_IRON, 5), "Input 0 is " + inputSlot(upgradedSlots, 0).getStack());
        check(stackIs(outputSlot(upgradedSlots, 0).getStack(), Items.IRON_INGOT, 2), "Output 0 is " + outputSlot(upgradedSlots, 0).getStack());
        check(stackIs(inputSlot(upgradedSlots, 1).getStack(), Items.RAW_COPPER, 3), "Input 1 is " + inputSlot(upgradedSlots, 1).getStack());
        check(inputSlot(upgradedSlots, 2).isEmpty() && outputSlot(upgradedSlots, 2).isEmpty(), "Untouched process was filled");
        check(inputSlot(upgradedSlots, 3).isEmpty() && inputSlot(upgradedSlots, 4).isEmpty(), "New processes received items");
        helper.succeed();
    }

    private static TileEntityFactory<?> place(GameTestHelper helper, BlockPos pos, FactoryTier tier, FactoryType type) {
        helper.setBlock(pos, MekanismBlocks.getFactory(tier, type).getBlock());
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityFactory, type + " " + tier + " block entity: " + blockEntity);
        return (TileEntityFactory<?>) blockEntity;
    }

    /**
     * Smelting, enriching and crushing factories store each process as an input slot then an output slot, and the
     * energy slot last.
     */
    private static IInventorySlot inputSlot(List<IInventorySlot> slots, int process) {
        return slots.get(process * 2);
    }

    private static IInventorySlot outputSlot(List<IInventorySlot> slots, int process) {
        return slots.get(process * 2 + 1);
    }

    private static void fillEnergy(TileEntityMekanism machine) {
        FloatingLong leftover = ((IStrictEnergyHandler) machine).insertEnergy(FloatingLong.createConst(1_000_000), Action.EXECUTE);
        check(leftover.greaterThan(FloatingLong.ZERO) && energy(machine).greaterThan(FloatingLong.ZERO),
              "Factory energy stored " + energy(machine) + " with leftover " + leftover);
    }

    private static FloatingLong energy(TileEntityMekanism machine) {
        return machine.getEnergyContainers(null).get(0).getEnergy();
    }

    private static boolean stackIs(ItemStack stack, Item item, int count) {
        return stack.is(item) && stack.getCount() == count;
    }

    private static Class<? extends TileEntityFactory<?>> tileClass(FactoryType type) {
        return switch (type) {
            case SMELTING, ENRICHING, CRUSHING -> TileEntityItemStackToItemStackFactory.class;
            case COMBINING -> TileEntityCombiningFactory.class;
            case SAWING -> TileEntitySawingFactory.class;
            case INFUSING -> TileEntityMetallurgicInfuserFactory.class;
            case COMPRESSING, PURIFYING, INJECTING -> TileEntityItemStackGasToItemStackFactory.class;
        };
    }
}
