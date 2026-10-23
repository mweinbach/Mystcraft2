/*
 * Mekanism 1.19.2 port - development-only Fabric GameTest fixtures.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import ports.mekanism.fabric.FabricFluidPhysics;
import ports.mekanism.lib.fluids.FluidType;
import ports.mekanism.lib.fluids.ForgeFlowingFluid;
import ports.mekanism.lib.patched.ForgeLiquidBlock;

/**
 * A fluid built like Mekanism's, on the port's {@link ForgeFlowingFluid} and {@link FluidType}, whose type turns off
 * swimming, drowning and pushing. No Mekanism fluid does, so this one shows the Fabric adapter reads the type rather
 * than assuming Forge's defaults. It is registered under this test mod's namespace and never ships.
 */
public final class FabricTestFluids implements ModInitializer {

    public static final FluidType INERT_TYPE = new FluidType(FluidType.Properties.create()
          .descriptionId("block.mekanism_gametest.inert_fluid")
          .canSwim(false)
          .canDrown(false)
          .canPushEntity(false)
          .fallDistanceModifier(0.1F)
          .pathType(BlockPathTypes.WALKABLE));
    public static ForgeFlowingFluid.Source INERT;
    public static ForgeFlowingFluid.Flowing INERT_FLOWING;
    public static LiquidBlock INERT_BLOCK;

    @Override
    public void onInitialize() {
        ForgeFlowingFluid.Properties properties = new ForgeFlowingFluid.Properties(() -> INERT_TYPE, () -> INERT, () -> INERT_FLOWING)
              .block(() -> INERT_BLOCK);
        INERT = Registry.register(Registry.FLUID, id("inert_fluid"), new ForgeFlowingFluid.Source(properties));
        INERT_FLOWING = Registry.register(Registry.FLUID, id("flowing_inert_fluid"), new ForgeFlowingFluid.Flowing(properties));
        INERT_BLOCK = Registry.register(Registry.BLOCK, id("inert_fluid"),
              new ForgeLiquidBlock(() -> INERT, BlockBehaviour.Properties.of(Material.WATER).noCollission().strength(100.0F).noLootTable()));
        FabricFluidPhysics.register(INERT);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("mekanism_gametest", path);
    }
}
