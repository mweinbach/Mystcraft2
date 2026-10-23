/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.integration.energy.IEnergyCompat;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.gametest.forge.FluxNetworksTestAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

/**
 * Upstream's Flux Networks energy compat: Mekanism blocks offer Flux's long-based energy capability, so a Flux Plug or Point
 * moves more than {@link Integer#MAX_VALUE} FE per operation, which Forge Energy cannot. Flux Networks for Fabric talks
 * Team Reborn Energy, which is already long-based, so this capability has no Fabric counterpart.
 */
public class ForgeFluxCapabilityGameTests {

    private static final long MORE_THAN_AN_INT = 5_000_000_000L;

    @GameTest(template = TEMPLATE)
    public static void mekanismOffersFluxsLongEnergyCapability(GameTestHelper helper) {
        IEnergyCompat flux = EnergyCompatUtils.getCompats().stream()
              .filter(compat -> compat.getClass().getSimpleName().equals("FNEnergyCompat"))
              .findFirst().orElse(null);
        check(flux != null, "The Forge JAR has no Flux Networks energy compat");
        check(EnergyCompatUtils.getCompats().indexOf(flux) == 1, "The Flux Networks compat is not checked before Forge Energy");
        boolean loaded = ModList.get().isLoaded(MekanismHooks.FLUX_NETWORKS_MOD_ID);
        check(flux.isUsable() == loaded, "The Flux Networks compat is " + (flux.isUsable() ? "usable" : "unusable") + " with Flux Networks "
                                         + (loaded ? "installed" : "absent"));

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.CREATIVE_ENERGY_CUBE.getBlock());
        TileEntityMekanism cube = (TileEntityMekanism) helper.getBlockEntity(pos);
        cube.setFacing(Direction.NORTH);
        ((IStrictEnergyHandler) cube).setEnergy(0, FloatingLong.MAX_VALUE);
        LazyOptional<?> front = cube.getCapability(flux.getCapability(), Direction.NORTH);
        if (!loaded) {
            check(!front.isPresent(), "The creative cube offers the Flux Networks capability without Flux Networks");
            helper.succeed();
            return;
        }
        check(front.isPresent(), "The creative cube's output side does not offer the Flux Networks capability");
        long extracted = FluxNetworksTestAccess.extract(front.orElseThrow(IllegalStateException::new), MORE_THAN_AN_INT);
        check(extracted == MORE_THAN_AN_INT, "Extracted " + extracted + " of " + MORE_THAN_AN_INT + " FE through the Flux Networks capability");
        helper.succeed();
    }
}
