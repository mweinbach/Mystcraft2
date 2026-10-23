/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.lang.reflect.Method;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.Mekanism;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/**
 * Flux Networks on either loader: a Flux Plug takes energy from a Mekanism energy cube and a Flux Point on the same network
 * delivers it to another cube. Forge Flux reads Mekanism through the Flux Networks or Forge Energy capability, Fabric Flux
 * through Team Reborn Energy. Run with {@code -PwithFluxNetworks}; without the mod the test checks the integration is off.
 * <p>
 * The network is set up by reflection. Flux's device classes implement Porting Lib interfaces on Fabric, and the tests do
 * not compile against either loader's Flux jar.
 */
public class FluxNetworksGameTests {

    private static final FloatingLong STORED = FloatingLong.createConst(1_000_000);

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void fluxPlugAndPointCarryMekanismEnergy(GameTestHelper helper) {
        boolean loaded = ModList.get().isLoaded(MekanismHooks.FLUX_NETWORKS_MOD_ID);
        check(Mekanism.hooks.FluxNetworksLoaded == loaded, "FluxNetworksLoaded is " + Mekanism.hooks.FluxNetworksLoaded + " with Flux Networks "
                                                           + (loaded ? "installed" : "absent"));
        Block plugBlock = fluxBlock("flux_plug");
        Block pointBlock = fluxBlock("flux_point");
        if (!loaded) {
            check(plugBlock == Blocks.AIR && pointBlock == Blocks.AIR, "Flux blocks are registered without Flux Networks");
            helper.succeed();
            return;
        }
        check(plugBlock != Blocks.AIR && pointBlock != Blocks.AIR, "Flux Networks is loaded but has no flux_plug or flux_point");

        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos plugPos = sourcePos.east();
        BlockPos pointPos = new BlockPos(2, 1, 4);
        BlockPos sinkPos = pointPos.east();
        helper.setBlock(sourcePos, MekanismBlocks.BASIC_ENERGY_CUBE.getBlock());
        helper.setBlock(sinkPos, MekanismBlocks.BASIC_ENERGY_CUBE.getBlock());
        TileEntityMekanism source = (TileEntityMekanism) helper.getBlockEntity(sourcePos);
        TileEntityMekanism sink = (TileEntityMekanism) helper.getBlockEntity(sinkPos);
        // An energy cube outputs from its front and takes energy on every other side. The source faces the plug; the sink faces
        // away from the point.
        source.setFacing(Direction.EAST);
        sink.setFacing(Direction.EAST);
        ((IStrictEnergyHandler) source).setEnergy(0, STORED);

        helper.setBlock(plugPos, plugBlock);
        helper.setBlock(pointPos, pointBlock);
        BlockEntity plug = helper.getBlockEntity(plugPos);
        BlockEntity point = helper.getBlockEntity(pointPos);
        Object network = createNetwork(helper.makeMockPlayer());
        connect(plug, network);
        connect(point, network);

        helper.succeedWhen(() -> {
            FloatingLong delivered = ((IStrictEnergyHandler) sink).getEnergy(0);
            check(!delivered.isZero(), "The Flux Point delivered no energy to the Mekanism cube");
            check(((IStrictEnergyHandler) source).getEnergy(0).smallerThan(STORED), "The Flux Plug took no energy from the Mekanism cube");
            deleteNetwork(network);
        });
    }

    private static Block fluxBlock(String path) {
        return Registry.BLOCK.get(new ResourceLocation(MekanismHooks.FLUX_NETWORKS_MOD_ID, path));
    }

    private static Object networkData() throws ReflectiveOperationException {
        return Class.forName("sonar.fluxnetworks.common.connection.FluxNetworkData").getMethod("getInstance").invoke(null);
    }

    private static Object createNetwork(Player owner) {
        try {
            Object data = networkData();
            Class<?> securityLevel = Class.forName("sonar.fluxnetworks.api.network.SecurityLevel");
            Method create = data.getClass().getMethod("createNetwork", Player.class, String.class, int.class, securityLevel, String.class);
            Object network = create.invoke(data, owner, "Mekanism GameTest", 0x2A8BD8, securityLevel.getField("PUBLIC").get(null), "");
            check(network != null, "Flux Networks refused to create a network");
            return network;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Flux Networks' network API changed", e);
        }
    }

    private static void connect(BlockEntity device, Object network) {
        try {
            Class<?> networkClass = Class.forName("sonar.fluxnetworks.common.connection.FluxNetwork");
            Object connected = device.getClass().getMethod("connect", networkClass).invoke(device, network);
            check(Boolean.TRUE.equals(connected), device + " did not join the Flux network");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Flux Networks' device API changed", e);
        }
    }

    private static void deleteNetwork(Object network) {
        try {
            Object data = networkData();
            data.getClass().getMethod("deleteNetwork", Class.forName("sonar.fluxnetworks.common.connection.FluxNetwork"))
                  .invoke(data, network);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Flux Networks' network API changed", e);
        }
    }
}
