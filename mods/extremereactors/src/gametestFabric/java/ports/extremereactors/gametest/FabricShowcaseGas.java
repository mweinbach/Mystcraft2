/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import com.mojang.brigadier.CommandDispatcher;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.MultiblockReactor;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.multiblock.AbstractMultiblockPart;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /ershowcase gas} on the Fabric showcase client: a running reactor whose Mekanism port feeds steam through a
 * pressurized tube into a chemical tank. Only registered when Mekanism is loaded, and never shipped.
 */
final class FabricShowcaseGas {

    static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {

        if (!s_ticking) {

            s_ticking = true;
            ServerTickEvents.END_SERVER_TICK.register(FabricShowcaseGas::tick);
        }

        dispatcher.register(Commands.literal("ershowcase")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("gas").executes(context -> {

                    build(context.getSource().getLevel(), context.getSource().getPlayerOrException());
                    context.getSource().sendSuccess(Component.literal(
                            "Building a reactor whose Mekanism port feeds steam into a tube and a chemical tank"), false);
                    return 1;
                })));
    }

    private static void build(final ServerLevel level, final ServerPlayer player) {

        final BlockPos origin = player.blockPosition().offset(2, 0, 2);

        s_level = level;
        s_player = player;
        s_controller = origin.offset(0, 1, 1);
        s_access = origin.offset(1, 1, 0);
        s_tank = origin.offset(4, 1, 1);
        s_filled = false;
        s_announced = false;

        player.setGameMode(GameType.CREATIVE);
        level.setDayTime(6000);

        for (final BlockPos position : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(10, 5, 5))) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        for (final BlockPos position : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(10, -1, 5))) {
            level.setBlock(position, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        }

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        parts.put(new BlockPos(1, 2, 1), Content.Blocks.REACTOR_CONTROLROD_REINFORCED.get());
        parts.put(new BlockPos(1, 1, 1), Content.Blocks.REACTOR_FUELROD_REINFORCED.get());
        parts.put(new BlockPos(0, 1, 1), Content.Blocks.REACTOR_CONTROLLER_REINFORCED.get());
        parts.put(new BlockPos(1, 1, 0), Content.Blocks.REACTOR_SOLID_ACCESSPORT_REINFORCED.get());
        parts.put(new BlockPos(1, 1, 2), Content.Blocks.REACTOR_FLUIDPORT_FORGE_PASSIVE_REINFORCED.get());
        parts.put(new BlockPos(2, 1, 1), Content.Blocks.REACTOR_FLUIDPORT_MEKANISM_PASSIVE_REINFORCED.get());

        final Block casing = Content.Blocks.REACTOR_CASING_REINFORCED.get();

        for (int x = 0; x < 3; ++x) {
            for (int y = 0; y < 3; ++y) {
                for (int z = 0; z < 3; ++z) {

                    final BlockPos relative = new BlockPos(x, y, z);
                    final boolean shell = 0 == x || 0 == y || 0 == z || 2 == x || 2 == y || 2 == z;

                    level.setBlock(origin.offset(relative), parts.getOrDefault(relative, shell ? casing : Blocks.AIR)
                            .defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        final BlockPos tube = origin.offset(3, 1, 1);

        level.setBlock(tube, MekanismBlocks.BASIC_PRESSURIZED_TUBE.getBlock().defaultBlockState(), Block.UPDATE_ALL);

        BlockState tank = MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock().defaultBlockState();

        if (tank.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            tank = tank.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        }

        level.setBlock(s_tank, tank, Block.UPDATE_ALL);

        if (level.getBlockEntity(tube) instanceof TileEntityPressurizedTube pressurized) {

            pressurized.getTransmitter().setConnectionTypeRaw(Direction.WEST, ConnectionType.PULL);
            pressurized.getTransmitter().setConnectionTypeRaw(Direction.EAST, ConnectionType.PUSH);
        }

        final BlockPos stand = origin.offset(8, 1, 1);

        player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 90.0f, 12.0f);
    }

    private static void tick(final MinecraftServer server) {

        if (null == s_level || s_level.getServer() != server || null == s_controller) {
            return;
        }

        if (!s_filled) {

            final Optional<MultiblockReactor> reactor = controller(s_level, s_controller);

            if (reactor.isEmpty()) {
                return;
            }

            reactor.get().getFluidHandler(IoDirection.Input)
                    .ifPresent(handler -> handler.fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE));
            insertFuel(s_level, s_access);
            reactor.get().setControlRodsInsertionRatio(0);
            reactor.get().setMachineActive(true);
            s_filled = true;
            return;
        }

        if (s_announced || null == s_player || null == s_tank) {
            return;
        }

        if (s_level.getBlockEntity(s_tank) instanceof TileEntityChemicalTank chemical
                && chemical.getGasTank().getStored() > 0) {

            s_announced = true;
            s_player.displayClientMessage(Component.literal("The chemical tank holds "
                    + chemical.getGasTank().getStored() + " of " + chemical.getGasTank().getType()), false);
        }
    }

    private static Optional<MultiblockReactor> controller(final ServerLevel level, final BlockPos position) {

        if (level.getBlockEntity(position) instanceof AbstractMultiblockPart<?> part
                && part.getMultiblockController().orElse(null) instanceof MultiblockReactor reactor
                && reactor.isAssembled()) {
            return Optional.of(reactor);
        }

        return Optional.empty();
    }

    private static void insertFuel(final ServerLevel level, final BlockPos access) {

        final IItemHandler handler = CapabilityHelper.getCapability(level, access, CapabilityHelper.ITEM_HANDLER,
                Direction.NORTH).orElse(null);

        if (null == handler) {
            return;
        }

        ItemStack fuel = new ItemStack(Content.Items.YELLORIUM_INGOT.get(), 64);

        for (int slot = 0; slot < handler.getSlots() && !fuel.isEmpty(); ++slot) {
            fuel = handler.insertItem(slot, fuel, false);
        }
    }

    @Nullable
    private static ServerLevel s_level;
    @Nullable
    private static ServerPlayer s_player;
    @Nullable
    private static BlockPos s_controller;
    @Nullable
    private static BlockPos s_access;
    @Nullable
    private static BlockPos s_tank;
    private static boolean s_filled;
    private static boolean s_announced;
    private static boolean s_ticking;

    private FabricShowcaseGas() {
    }
}
