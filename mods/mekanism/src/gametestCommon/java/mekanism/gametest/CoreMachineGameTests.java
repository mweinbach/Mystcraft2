/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Core machines exist in the world and process their recipes on the loader under test.
 */
public class CoreMachineGameTests {

    public static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void coreMachinesHaveBlockEntities(GameTestHelper helper) {
        List<BlockRegistryObject<?, ?>> machines = List.of(MekanismBlocks.ENRICHMENT_CHAMBER, MekanismBlocks.CRUSHER, MekanismBlocks.ENERGIZED_SMELTER,
              MekanismBlocks.OSMIUM_COMPRESSOR, MekanismBlocks.METALLURGIC_INFUSER, MekanismBlocks.PURIFICATION_CHAMBER,
              MekanismBlocks.CHEMICAL_INJECTION_CHAMBER, MekanismBlocks.ELECTROLYTIC_SEPARATOR, MekanismBlocks.BASIC_ENERGY_CUBE);
        for (int i = 0; i < machines.size(); i++) {
            BlockPos pos = new BlockPos(i % 4 * 2, 1, i / 4 * 2);
            helper.setBlock(pos, machines.get(i).getBlock());
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            check(blockEntity instanceof TileEntityMekanism, machines.get(i).getInternalRegistryName() + " has no Mekanism block entity: " + blockEntity);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void enrichmentChamberEnrichesOre(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.ENRICHMENT_CHAMBER.getBlock());
        TileEntityMekanism chamber = (TileEntityMekanism) helper.getBlockEntity(pos);
        FloatingLong leftover = ((IStrictEnergyHandler) chamber).insertEnergy(FloatingLong.createConst(20_000), Action.EXECUTE);
        check(leftover.smallerThan(FloatingLong.createConst(20_000)), "The enrichment chamber accepted no energy");
        List<IInventorySlot> slots = chamber.getInventorySlots(null);
        slots.get(0).setStack(new ItemStack(item("osmium_ore"), 2));
        Item dust = item("dust_osmium");
        helper.succeedWhen(() -> {
            ItemStack output = slots.get(1).getStack();
            if (!output.is(dust) || output.getCount() < 4) {
                throw new GameTestAssertException("Expected 4 osmium dust, found " + output);
            }
        });
    }

    /**
     * Crushing iron ingots matches them through {@code forge:ingots/iron}, a tag only Forge itself defines.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void crusherCrushesIngotsThroughForgeTags(GameTestHelper helper) {
        TileEntityMekanism crusher = poweredMachine(helper, MekanismBlocks.CRUSHER);
        List<IInventorySlot> slots = crusher.getInventorySlots(null);
        slots.get(0).setStack(new ItemStack(Items.IRON_INGOT, 2));
        Item dust = item("dust_iron");
        helper.succeedWhen(() -> {
            ItemStack output = slots.get(1).getStack();
            if (!output.is(dust) || output.getCount() < 2) {
                throw new GameTestAssertException("Expected 2 iron dust, found " + output);
            }
        });
    }

    /**
     * Redstone converts to the redstone infuse type through {@code forge:dusts/redstone}, and the infusing recipe asks for
     * it through the {@code mekanism:redstone} infuse type tag.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void metallurgicInfuserInfusesThroughChemicalTags(GameTestHelper helper) {
        TileEntityMekanism infuser = poweredMachine(helper, MekanismBlocks.METALLURGIC_INFUSER);
        List<IInventorySlot> slots = infuser.getInventorySlots(null);
        check(slots.get(0).isItemValid(new ItemStack(Items.REDSTONE)), "The infusion slot rejects redstone");
        slots.get(0).setStack(new ItemStack(Items.REDSTONE, 2));
        check(slots.get(1).isItemValid(new ItemStack(Items.IRON_INGOT)), "The input slot rejects iron ingots");
        slots.get(1).setStack(new ItemStack(Items.IRON_INGOT, 2));
        Item alloy = item("alloy_infused");
        helper.succeedWhen(() -> {
            ItemStack output = slots.get(2).getStack();
            if (!output.is(alloy) || output.getCount() < 2) {
                throw new GameTestAssertException("Expected 2 infused alloys, found " + output);
            }
        });
    }

    static TileEntityMekanism poweredMachine(GameTestHelper helper, BlockRegistryObject<?, ?> block) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block.getBlock());
        TileEntityMekanism machine = (TileEntityMekanism) helper.getBlockEntity(pos);
        FloatingLong leftover = ((IStrictEnergyHandler) machine).insertEnergy(FloatingLong.createConst(100_000), Action.EXECUTE);
        check(leftover.smallerThan(FloatingLong.createConst(100_000)), block.getInternalRegistryName() + " accepted no energy");
        return machine;
    }

    static Item item(String path) {
        ResourceLocation name = new ResourceLocation("mekanism", path);
        check(Registry.ITEM.containsKey(name), "Missing item " + name);
        return Registry.ITEM.get(name);
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
