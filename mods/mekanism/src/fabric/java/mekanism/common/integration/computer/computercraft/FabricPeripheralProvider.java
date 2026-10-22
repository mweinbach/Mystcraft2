/*
 * Mekanism 1.19.2 port - Fabric adapter for the ComputerCraft peripheral capability.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.integration.computer.computercraft;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import mekanism.common.integration.computer.IComputerTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.CapabilityManager;
import ports.mekanism.lib.common.capabilities.CapabilityToken;

/**
 * CC: Restitched has no Forge capability, so computers ask registered providers. The peripheral instance is the one
 * the shared capability resolver caches, which is the same object Forge's ComputerCraft reads.
 */
public final class FabricPeripheralProvider implements IPeripheralProvider {

    private static final Capability<IPeripheral> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    private FabricPeripheralProvider() {
    }

    public static void register() {
        ComputerCraftAPI.registerPeripheralProvider(new FabricPeripheralProvider());
    }

    @Override
    public IPeripheral getPeripheral(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IComputerTile computer) || !computer.hasComputerSupport()) {
            return null;
        }
        return blockEntity.getCapability(CAPABILITY, side).orElse(null);
    }
}
