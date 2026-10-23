/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import mekanism.common.content.network.transmitter.BoxedPressurizedTube;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Mekanism's own blocks, used as a player would use them to take gas from one of ours. Kept apart from the shared
 * tests so Mekanism's classes only load with Mekanism. Both loaders compile this against Mekanism: Forge against
 * Mekanism 10.3.9, Fabric against this repository's Mekanism port.
 */
final class MekanismConsumers {

    /**
     * Place a basic pressurized tube and a basic chemical tank on the east side of it, both with their default
     * settings, as a player places them.
     */
    static void placeTubeAndTank(final GameTestHelper helper, final BlockPos tube, final BlockPos tank) {

        BlockState tankState = MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock().defaultBlockState();

        if (tankState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            tankState = tankState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        }

        helper.setBlock(tube, MekanismBlocks.BASIC_PRESSURIZED_TUBE.getBlock().defaultBlockState());
        helper.setBlock(tank, tankState);
    }

    /**
     * Switch a tube side to pull and its opposite to push, the steps Mekanism's configurator takes when a player
     * sneak-right-clicks those sides. A pulling tube side drains its neighbour every tick.
     */
    static void pullThrough(final GameTestHelper helper, final BlockPos tube, final Direction pullSide) {

        if (!(helper.getBlockEntity(tube) instanceof TileEntityPressurizedTube tile)) {
            throw new GameTestAssertException("no pressurized tube at " + tube);
        }

        final BoxedPressurizedTube transmitter = tile.getTransmitter();

        configure(transmitter, pullSide, ConnectionType.PULL);
        configure(transmitter, pullSide.getOpposite(), ConnectionType.PUSH);
    }

    private static void configure(final BoxedPressurizedTube transmitter, final Direction side, final ConnectionType type) {

        transmitter.setConnectionTypeRaw(side, type);
        transmitter.onModeChange(side);
        transmitter.refreshConnections();
        transmitter.notifyTileChange();
    }

    private MekanismConsumers() {
    }
}
