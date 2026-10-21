/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.registries.GeneratorsFluids;
import mekanism.generators.common.registries.GeneratorsItems;
import mekanism.generators.common.tile.TileEntityHeatGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The Generators module jar is present and its registries have run. Simple generator gameplay is
 * {@link SimpleGeneratorGameTests}.
 */
public class GeneratorsModuleGameTests {

    public static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void generatorsModuleRegistersContent(GameTestHelper helper) {
        ResourceLocation heat = new ResourceLocation(MekanismGenerators.MODID, "heat_generator");
        check(Registry.BLOCK.get(heat) == GeneratorsBlocks.HEAT_GENERATOR.getBlock(), "heat generator block is not registered");
        check(Registry.ITEM.get(heat) == GeneratorsBlocks.HEAT_GENERATOR.asItem(), "heat generator item is not registered");
        ResourceLocation panel = new ResourceLocation(MekanismGenerators.MODID, "solar_panel");
        check(Registry.ITEM.get(panel) == GeneratorsItems.SOLAR_PANEL.asItem(), "solar panel is not registered");
        ResourceLocation ethanol = new ResourceLocation(MekanismGenerators.MODID, "bioethanol");
        check(Registry.FLUID.get(ethanol) == GeneratorsFluids.BIOETHANOL.getStillFluid(), "bioethanol is not registered");
        check(Registry.ITEM.get(new ResourceLocation(MekanismGenerators.MODID, "bioethanol_bucket")) == GeneratorsFluids.BIOETHANOL.getBucket(),
              "bioethanol bucket is not registered");
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, GeneratorsBlocks.HEAT_GENERATOR.getBlock());
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityHeatGenerator, "heat generator has no block entity: " + blockEntity);
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
