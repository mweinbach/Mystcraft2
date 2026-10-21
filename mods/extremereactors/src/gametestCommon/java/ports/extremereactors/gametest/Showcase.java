/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.MultiblockReactor;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part.ReactorFluidPortEntity;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.MultiblockTurbine;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.VentSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.extremereactors.corelib.IActivableMachine;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.multiblock.AbstractMultiblockController;
import ports.extremereactors.multiblock.AbstractMultiblockPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Development-only machines for screenshots, never shipped in a production JAR. {@link #build} lays out a running
 * passive reactor, an active reactor, a turbine, a reprocessor and a fluidizer in a row; {@link #open} then opens the
 * screen of one of their parts. The loader adapters register the {@code /ershowcase} command and call
 * {@link #tick} every server tick.
 */
public final class Showcase {

    /** The parts with a screen, by the name {@code /ershowcase open} takes. */
    public static Collection<String> screens() {
        return SCREENS.keySet();
    }

    public static void build(final ServerLevel level, final BlockPos origin) {

        reset(level);

        final BlockPos base = origin.offset(2, 0, 2);

        fill(level, origin.offset(-2, -1, -2), origin.offset(40, -1, 10), Blocks.SMOOTH_STONE);
        fill(level, origin.offset(-2, 0, -2), origin.offset(40, 8, 10), Blocks.AIR);

        passiveReactor(level, base);
        activeReactor(level, base.offset(8, 0, 0));
        turbine(level, base.offset(16, 0, 0));
        reprocessor(level, base.offset(24, 0, 1));
        fluidizer(level, base.offset(31, 0, 1));
    }

    /** A large running reactor and turbine with every reinforced port on their front faces, for texture reviews. */
    public static void buildPlant(final ServerLevel level, final BlockPos origin) {

        reset(level);

        final BlockPos base = origin.offset(2, 0, 2);

        fill(level, origin.offset(-2, -1, -2), origin.offset(24, -1, 12), Blocks.SMOOTH_STONE);
        fill(level, origin.offset(-2, 0, -2), origin.offset(24, 13, 12), Blocks.AIR);

        largeReactor(level, base);
        largeTurbine(level, base.offset(12, 0, 1));
    }

    /** Opens the screen of the named part for the player, as if they right-clicked it with an empty hand. */
    public static boolean open(final ServerPlayer player, final String name) {

        final BlockPos position = SCREENS.get(name);

        if (null == position) {
            return false;
        }

        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);

        // Menus close at once when the player is more than 8 blocks away, so stand in front of the part.
        if (player.distanceToSqr(Vec3.atCenterOf(position)) > VIEW_DISTANCE * VIEW_DISTANCE) {
            player.teleportTo(player.getLevel(), position.getX() + 0.5, position.getY() - 1,
                    position.getZ() - VIEW_DISTANCE + 0.5, 0.0f, 10.0f);
        }

        player.getLevel().getBlockState(position).use(player.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        return true;
    }

    /** Sets the control rods of every showcase reactor, as their control rod screens would. */
    public static void rods(final ServerLevel level, final int insertion) {
        reactors(level).forEach(reactor -> reactor.setControlRodsInsertionRatio(insertion));
    }

    /** SCRAMs every showcase reactor, as the SCRAM button of its controller screen does. */
    public static void scram(final ServerLevel level) {
        reactors(level).forEach(reactor -> {

            reactor.setControlRodsInsertionRatio(100);
            reactor.setMachineActive(false);
        });
    }

    /** Turns every showcase reactor back on. */
    public static void start(final ServerLevel level) {
        reactors(level).forEach(reactor -> reactor.setMachineActive(true));
    }

    /** Turns every showcase turbine on or off, as the button of its controller screen does. */
    public static void turbines(final ServerLevel level, final boolean active) {
        SCREENS.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("turbine_controller"))
                .flatMap(entry -> controller(level, entry.getValue()).stream())
                .filter(MultiblockTurbine.class::isInstance)
                .map(MultiblockTurbine.class::cast)
                .distinct()
                .forEach(turbine -> turbine.setMachineActive(active));
    }

    /** One line per machine: assembled, or the validation error that stops it. */
    public static List<Component> status(final ServerLevel level) {

        final List<Component> lines = new ArrayList<>();

        for (final Map.Entry<String, BlockPos> entry : SCREENS.entrySet()) {

            if (!entry.getKey().endsWith("controller")) {
                continue;
            }

            final String state = controller(level, entry.getValue())
                    .map(controller -> controller.isAssembled() ? "assembled" : controller.getLastError()
                            .map(error -> error.getChatMessage().getString())
                            .orElse("not assembled"))
                    .orElse("no controller");

            lines.add(Component.literal(entry.getKey() + ": " + state));
        }

        return lines;
    }

    /**
     * Fills the machines once they assembled, powers the reprocessor and fluidizer, waters the
     * reprocessor, refuels the large reactor and vents the active reactors' steam.
     */
    public static void tick(final MinecraftServer server) {

        if (null == s_level || s_level.getServer() != server) {
            return;
        }

        s_pending.removeIf(task -> task.get());

        for (final BlockPos position : s_powered) {
            CapabilityHelper.getCapability(s_level, position, CapabilityHelper.ENERGY, Direction.NORTH)
                    .ifPresent(energy -> energy.receiveEnergy(POWER_PER_TICK, false));
        }

        for (final BlockPos position : s_watered) {
            insertFluid(s_level, position, Fluids.WATER, WATER_PER_TICK);
        }

        for (final BlockPos position : s_fueled) {
            insertItems(s_level, position, new ItemStack(Content.Items.YELLORIUM_INGOT.get(), 64));
        }

        for (final BlockPos position : s_vented) {
            controller(s_level, position)
                    .filter(MultiblockReactor.class::isInstance)
                    .flatMap(reactor -> ((MultiblockReactor) reactor).getFluidHandler(IoDirection.Output))
                    .ifPresent(steam -> steam.drain(STEAM_PER_TICK, IFluidHandler.FluidAction.EXECUTE));
        }
    }

    //region machines

    private static void passiveReactor(final ServerLevel level, final BlockPos at) {

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        for (final int[] rod : new int[][] {{1, 1}, {3, 3}, {1, 3}, {3, 1}}) {

            for (int y = 1; y < 4; ++y) {
                parts.put(new BlockPos(rod[0], y, rod[1]), Content.Blocks.REACTOR_FUELROD_BASIC.get());
            }

            parts.put(new BlockPos(rod[0], 4, rod[1]), Content.Blocks.REACTOR_CONTROLROD_BASIC.get());
        }

        for (final BlockPos interior : BlockPos.betweenClosed(1, 1, 1, 3, 3, 3)) {
            parts.putIfAbsent(interior.immutable(), Content.Blocks.GRAPHITE_BLOCK.get());
        }

        parts.put(new BlockPos(2, 2, 0), Content.Blocks.REACTOR_CONTROLLER_BASIC.get());
        parts.put(new BlockPos(1, 2, 0), Content.Blocks.REACTOR_SOLID_ACCESSPORT_BASIC.get());
        parts.put(new BlockPos(3, 2, 0), Content.Blocks.REACTOR_REDSTONEPORT_BASIC.get());
        parts.put(new BlockPos(2, 1, 0), Content.Blocks.REACTOR_CHARGINGPORT_FE_BASIC.get());
        parts.put(new BlockPos(2, 3, 0), Content.Blocks.REACTOR_GLASS_BASIC.get());
        parts.put(new BlockPos(4, 2, 2), Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_BASIC.get());

        cuboid(level, at, 5, 5, 5, Content.Blocks.REACTOR_CASING_BASIC.get(), parts);
        screen("reactor_controller", at.offset(2, 2, 0));
        screen("reactor_solid_accessport", at.offset(1, 2, 0));
        screen("reactor_redstoneport", at.offset(3, 2, 0));
        screen("reactor_chargingport", at.offset(2, 1, 0));
        screen("reactor_controlrod", at.offset(1, 4, 1));

        whenAssembled(level, at.offset(2, 2, 0), controller -> {

            insertItems(level, at.offset(1, 2, 0), new ItemStack(Content.Items.YELLORIUM_INGOT.get(), 64));
            activate(controller);
        });
    }

    private static void activeReactor(final ServerLevel level, final BlockPos at) {

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        for (int y = 1; y < 4; ++y) {
            parts.put(new BlockPos(2, y, 2), Content.Blocks.REACTOR_FUELROD_REINFORCED.get());
        }

        parts.put(new BlockPos(2, 4, 2), Content.Blocks.REACTOR_CONTROLROD_REINFORCED.get());
        parts.put(new BlockPos(2, 2, 0), Content.Blocks.REACTOR_CONTROLLER_REINFORCED.get());
        parts.put(new BlockPos(1, 2, 0), Content.Blocks.REACTOR_FLUID_ACCESSPORT_REINFORCED.get());
        parts.put(new BlockPos(3, 2, 0), Content.Blocks.REACTOR_FLUIDPORT_FORGE_PASSIVE_REINFORCED.get());
        parts.put(new BlockPos(2, 1, 0), Content.Blocks.REACTOR_CREATIVE_WATER_GENERATOR_REINFORCED.get());
        parts.put(new BlockPos(2, 3, 0), Content.Blocks.REACTOR_GLASS_REINFORCED.get());

        cuboid(level, at, 5, 5, 5, Content.Blocks.REACTOR_CASING_REINFORCED.get(), parts);
        screen("active_reactor_controller", at.offset(2, 2, 0));
        screen("reactor_fluid_accessport", at.offset(1, 2, 0));
        screen("reactor_fluidport", at.offset(3, 2, 0));
        s_vented.add(at.offset(2, 2, 0));

        whenAssembled(level, at.offset(2, 2, 0), controller -> {

            insertFluid(level, at.offset(1, 2, 0), Content.Fluids.YELLORIUM_SOURCE.get(), 4000);
            activate(controller);
        });
    }

    private static void turbine(final ServerLevel level, final BlockPos at) {

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        parts.put(new BlockPos(2, 0, 2), Content.Blocks.TURBINE_ROTORBEARING_BASIC.get());

        for (int y = 1; y < 4; ++y) {
            parts.put(new BlockPos(2, y, 2), Content.Blocks.TURBINE_ROTORSHAFT_BASIC.get());
        }

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            for (int y = 1; y < 3; ++y) {
                parts.put(new BlockPos(2 + direction.getStepX(), y, 2 + direction.getStepZ()),
                        Content.Blocks.TURBINE_ROTORBLADE_BASIC.get());
            }
        }

        for (int x = 1; x < 4; ++x) {
            for (int z = 1; z < 4; ++z) {
                if (x != 2 || z != 2) {
                    parts.put(new BlockPos(x, 3, z), Blocks.GOLD_BLOCK);
                }
            }
        }

        parts.put(new BlockPos(2, 2, 0), Content.Blocks.TURBINE_CONTROLLER_BASIC.get());
        parts.put(new BlockPos(1, 2, 0), Content.Blocks.TURBINE_FLUIDPORT_FORGE_PASSIVE_BASIC.get());
        parts.put(new BlockPos(3, 2, 0), Content.Blocks.TURBINE_REDSTONEPORT_BASIC.get());
        parts.put(new BlockPos(2, 1, 0), Content.Blocks.TURBINE_CHARGINGPORT_FE_BASIC.get());
        parts.put(new BlockPos(2, 3, 0), Content.Blocks.TURBINE_GLASS_BASIC.get());
        parts.put(new BlockPos(2, 2, 4), Content.Blocks.TURBINE_CREATIVE_STEAM_GENERATOR_BASIC.get());
        parts.put(new BlockPos(4, 2, 2), Content.Blocks.TURBINE_POWERTAP_FE_PASSIVE_BASIC.get());

        cuboid(level, at, 5, 5, 5, Content.Blocks.TURBINE_CASING_BASIC.get(), parts);
        screen("turbine_controller", at.offset(2, 2, 0));
        screen("turbine_fluidport", at.offset(1, 2, 0));
        screen("turbine_redstoneport", at.offset(3, 2, 0));
        screen("turbine_chargingport", at.offset(2, 1, 0));

        whenAssembled(level, at.offset(2, 2, 0), controller -> {

            if (controller instanceof MultiblockTurbine turbine) {
                turbine.setInductorEngaged(true);
            }

            activate(controller);
        });
    }

    /** 3x7x3, like upstream: the collector under the column, the waste injector on top. */
    private static void reprocessor(final ServerLevel level, final BlockPos at) {

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        parts.put(new BlockPos(1, 0, 1), Content.Blocks.REPROCESSOR_COLLECTOR.get());
        parts.put(new BlockPos(1, 6, 1), Content.Blocks.REPROCESSOR_WASTEINJECTOR.get());
        parts.put(new BlockPos(1, 3, 0), Content.Blocks.REPROCESSOR_CONTROLLER.get());
        parts.put(new BlockPos(0, 3, 1), Content.Blocks.REPROCESSOR_FLUIDINJECTOR.get());
        parts.put(new BlockPos(2, 3, 1), Content.Blocks.REPROCESSOR_POWERPORT.get());
        parts.put(new BlockPos(1, 1, 0), Content.Blocks.REPROCESSOR_OUTPUTPORT.get());

        for (int y = 1; y < 6; ++y) {
            parts.put(new BlockPos(1, y, 2), Content.Blocks.REPROCESSOR_GLASS.get());
        }

        parts.put(new BlockPos(1, 2, 0), Content.Blocks.REPROCESSOR_GLASS.get());

        cuboid(level, at, 3, 7, 3, Content.Blocks.REPROCESSOR_CASING.get(), parts);
        screen("reprocessor_controller", at.offset(1, 3, 0));
        screen("reprocessor_wasteinjector", at.offset(1, 6, 1));
        screen("reprocessor_outputport", at.offset(1, 1, 0));
        s_powered.add(at.offset(2, 3, 1));
        s_watered.add(at.offset(0, 3, 1));

        whenAssembled(level, at.offset(1, 3, 0), controller -> {

            insertItems(level, at.offset(1, 6, 1), new ItemStack(Content.Items.CYANITE_INGOT.get(), 48));
            activate(controller);
        });
    }

    private static void fluidizer(final ServerLevel level, final BlockPos at) {

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        parts.put(new BlockPos(1, 1, 0), Content.Blocks.FLUIDIZER_CONTROLLER.get());
        parts.put(new BlockPos(0, 1, 1), Content.Blocks.FLUIDIZER_SOLIDINJECTOR.get());
        parts.put(new BlockPos(2, 1, 1), Content.Blocks.FLUIDIZER_OUTPUTPORT.get());
        parts.put(new BlockPos(1, 1, 2), Content.Blocks.FLUIDIZER_POWERPORT.get());
        parts.put(new BlockPos(1, 2, 1), Content.Blocks.FLUIDIZER_GLASS.get());

        cuboid(level, at, 3, 3, 3, Content.Blocks.FLUIDIZER_CASING.get(), parts);
        screen("fluidizer_controller", at.offset(1, 1, 0));
        screen("fluidizer_solidinjector", at.offset(0, 1, 1));
        s_powered.add(at.offset(1, 1, 2));

        whenAssembled(level, at.offset(1, 1, 0), controller -> {

            insertItems(level, at.offset(0, 1, 1), new ItemStack(Content.Items.YELLORIUM_INGOT.get(), 32));
            activate(controller);
        });
    }

    /** 9x9x9 active reactor: nine fuel columns between graphite pillars, glass on two faces, a row of ports. */
    private static void largeReactor(final ServerLevel level, final BlockPos at) {

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        for (int x = 1; x < 8; ++x) {
            for (int z = 1; z < 8; ++z) {

                final boolean fuel = 0 == x % 2 && 0 == z % 2;
                final boolean graphite = 1 == x % 2 && 1 == z % 2 && x > 1 && x < 7 && z > 1 && z < 7;

                for (int y = 1; y < 8; ++y) {
                    if (fuel) {
                        parts.put(new BlockPos(x, y, z), Content.Blocks.REACTOR_FUELROD_REINFORCED.get());
                    } else if (graphite) {
                        parts.put(new BlockPos(x, y, z), Content.Blocks.GRAPHITE_BLOCK.get());
                    }
                }

                if (fuel) {
                    parts.put(new BlockPos(x, 8, z), Content.Blocks.REACTOR_CONTROLROD_REINFORCED.get());
                }
            }
        }

        for (int x = 2; x < 7; ++x) {
            for (int y = 3; y < 8; ++y) {
                parts.put(new BlockPos(x, y, 0), Content.Blocks.REACTOR_GLASS_REINFORCED.get());
                parts.put(new BlockPos(8, y, x), Content.Blocks.REACTOR_GLASS_REINFORCED.get());
            }
        }

        parts.put(new BlockPos(4, 2, 0), Content.Blocks.REACTOR_CONTROLLER_REINFORCED.get());
        parts.put(new BlockPos(1, 1, 0), Content.Blocks.REACTOR_SOLID_ACCESSPORT_REINFORCED.get());
        parts.put(new BlockPos(2, 1, 0), Content.Blocks.REACTOR_FLUID_ACCESSPORT_REINFORCED.get());
        parts.put(new BlockPos(3, 1, 0), Content.Blocks.REACTOR_REDSTONEPORT_REINFORCED.get());
        parts.put(new BlockPos(4, 1, 0), Content.Blocks.REACTOR_COMPUTERPORT_REINFORCED.get());
        parts.put(new BlockPos(5, 1, 0), Content.Blocks.REACTOR_FLUIDPORT_FORGE_PASSIVE_REINFORCED.get());
        parts.put(new BlockPos(6, 1, 0), Content.Blocks.REACTOR_FLUIDTPORT_FORGE_ACTIVE_REINFORCED.get());
        parts.put(new BlockPos(7, 1, 0), Content.Blocks.REACTOR_FLUIDPORT_MEKANISM_PASSIVE_REINFORCED.get());
        parts.put(new BlockPos(2, 2, 0), Content.Blocks.REACTOR_CREATIVE_WATER_GENERATOR_REINFORCED.get());
        parts.put(new BlockPos(5, 2, 0), Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_REINFORCED.get());
        parts.put(new BlockPos(6, 2, 0), Content.Blocks.REACTOR_POWERTAP_FE_ACTIVE_REINFORCED.get());
        parts.put(new BlockPos(7, 2, 0), Content.Blocks.REACTOR_CHARGINGPORT_FE_REINFORCED.get());

        cuboid(level, at, 9, 9, 9, Content.Blocks.REACTOR_CASING_REINFORCED.get(), parts);
        screen("large_reactor_controller", at.offset(4, 2, 0));
        s_vented.add(at.offset(4, 2, 0));

        whenAssembled(level, at.offset(4, 2, 0), controller -> {

            if (level.getBlockEntity(at.offset(6, 1, 0)) instanceof ReactorFluidPortEntity steamPort) {
                steamPort.setIoDirection(IoDirection.Output);
            }

            s_fueled.add(at.offset(1, 1, 0));
            activate(controller);
        });
    }

    /** 7x12x7 turbine: a bottom bearing, two-block blades along most of the shaft and ludicrite coils on top. */
    private static void largeTurbine(final ServerLevel level, final BlockPos at) {

        final Map<BlockPos, Block> parts = new LinkedHashMap<>();

        parts.put(new BlockPos(3, 0, 3), Content.Blocks.TURBINE_ROTORBEARING_REINFORCED.get());

        for (int y = 1; y < 11; ++y) {
            parts.put(new BlockPos(3, y, 3), Content.Blocks.TURBINE_ROTORSHAFT_REINFORCED.get());
        }

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            for (int y = 1; y < 7; ++y) {
                for (int distance = 1; distance < 3; ++distance) {
                    parts.put(new BlockPos(3 + direction.getStepX() * distance, y, 3 + direction.getStepZ() * distance),
                            Content.Blocks.TURBINE_ROTORBLADE_REINFORCED.get());
                }
            }
        }

        for (int y = 8; y < 11; ++y) {
            for (int x = 2; x < 5; ++x) {
                for (int z = 2; z < 5; ++z) {
                    if (x != 3 || z != 3) {
                        parts.put(new BlockPos(x, y, z), Content.Blocks.LUDICRITE_BLOCK.get());
                    }
                }
            }
        }

        for (int x = 1; x < 6; ++x) {
            for (int y = 2; y < 10; ++y) {
                parts.put(new BlockPos(x, y, 0), Content.Blocks.TURBINE_GLASS_REINFORCED.get());
            }
        }

        parts.put(new BlockPos(3, 1, 0), Content.Blocks.TURBINE_CONTROLLER_REINFORCED.get());
        parts.put(new BlockPos(1, 1, 0), Content.Blocks.TURBINE_FLUIDPORT_FORGE_PASSIVE_REINFORCED.get());
        parts.put(new BlockPos(2, 1, 0), Content.Blocks.TURBINE_REDSTONEPORT_REINFORCED.get());
        parts.put(new BlockPos(4, 1, 0), Content.Blocks.TURBINE_COMPUTERPORT_REINFORCED.get());
        parts.put(new BlockPos(5, 1, 0), Content.Blocks.TURBINE_CHARGINGPORT_FE_REINFORCED.get());
        parts.put(new BlockPos(6, 1, 2), Content.Blocks.TURBINE_POWERTAP_FE_ACTIVE_REINFORCED.get());
        parts.put(new BlockPos(6, 1, 3), Content.Blocks.TURBINE_POWERTAP_FE_PASSIVE_REINFORCED.get());
        parts.put(new BlockPos(6, 1, 4), Content.Blocks.TURBINE_FLUIDPORT_FORGE_ACTIVE_REINFORCED.get());
        parts.put(new BlockPos(3, 5, 6), Content.Blocks.TURBINE_CREATIVE_STEAM_GENERATOR_REINFORCED.get());

        cuboid(level, at, 7, 12, 7, Content.Blocks.TURBINE_CASING_REINFORCED.get(), parts);
        screen("large_turbine_controller", at.offset(3, 1, 0));

        whenAssembled(level, at.offset(3, 1, 0), controller -> {

            if (controller instanceof MultiblockTurbine turbine) {

                turbine.setInductorEngaged(true);
                turbine.setVentSetting(VentSetting.VentAll);
            }

            activate(controller);
        });
    }

    //endregion
    //region helpers

    private interface Filler {
        void fill(AbstractMultiblockController<?> controller);
    }

    /** Forgets the previous build, so its machines and pending fillers are no longer ticked. */
    private static void reset(final ServerLevel level) {

        SCREENS.clear();
        s_powered.clear();
        s_watered.clear();
        s_fueled.clear();
        s_vented.clear();
        s_pending.clear();
        s_level = level;
    }

    private static void screen(final String name, final BlockPos position) {
        SCREENS.put(name, position.immutable());
    }

    /** A cuboid with a shell of {@code shell} and an air interior, plus the given parts at their relative positions. */
    private static void cuboid(final ServerLevel level, final BlockPos at, final int sizeX, final int sizeY,
                               final int sizeZ, final Block shell, final Map<BlockPos, Block> parts) {

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {

                    final BlockPos relative = new BlockPos(x, y, z);
                    final boolean onShell = 0 == x || 0 == y || 0 == z || sizeX - 1 == x || sizeY - 1 == y ||
                            sizeZ - 1 == z;

                    level.setBlock(at.offset(relative), parts.getOrDefault(relative, onShell ? shell : Blocks.AIR)
                            .defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void fill(final ServerLevel level, final BlockPos from, final BlockPos to, final Block block) {

        for (final BlockPos position : BlockPos.betweenClosed(from, to)) {
            level.setBlock(position, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void whenAssembled(final ServerLevel level, final BlockPos part, final Filler filler) {
        s_pending.add(() -> controller(level, part)
                .filter(AbstractMultiblockController::isAssembled)
                .map(controller -> {

                    filler.fill(controller);
                    return true;
                })
                .orElse(false));
    }

    private static Stream<MultiblockReactor> reactors(final ServerLevel level) {
        return SCREENS.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("reactor_controller"))
                .flatMap(entry -> controller(level, entry.getValue()).stream())
                .filter(MultiblockReactor.class::isInstance)
                .map(MultiblockReactor.class::cast)
                .distinct();
    }

    private static Optional<AbstractMultiblockController<?>> controller(final ServerLevel level, final BlockPos part) {
        return level.getBlockEntity(part) instanceof AbstractMultiblockPart<?> entity ?
                entity.getMultiblockController()
                        .filter(AbstractMultiblockController.class::isInstance)
                        .map(controller -> (AbstractMultiblockController<?>) controller) :
                Optional.empty();
    }

    private static void activate(final AbstractMultiblockController<?> controller) {

        if (controller instanceof IActivableMachine machine) {
            machine.setMachineActive(true);
        }
    }

    private static void insertItems(final ServerLevel level, final BlockPos position, final ItemStack stack) {
        CapabilityHelper.getCapability(level, position, CapabilityHelper.ITEM_HANDLER, Direction.NORTH)
                .ifPresent(handler -> {

                    ItemStack remainder = stack.copy();

                    for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); ++slot) {
                        remainder = handler.insertItem(slot, remainder, false);
                    }
                });
    }

    private static void insertFluid(final ServerLevel level, final BlockPos position, final Fluid fluid,
                                    final int amount) {
        CapabilityHelper.getCapability(level, position, CapabilityHelper.FLUID_HANDLER, Direction.NORTH)
                .ifPresent(handler -> handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE));
    }

    private static final int POWER_PER_TICK = 2000;
    private static final int WATER_PER_TICK = 100;
    private static final int STEAM_PER_TICK = 4000;
    private static final int VIEW_DISTANCE = 4;
    private static final Map<String, BlockPos> SCREENS = new LinkedHashMap<>();
    private static final List<BlockPos> s_powered = new ArrayList<>();
    private static final List<BlockPos> s_watered = new ArrayList<>();
    /** Access ports kept stocked with fuel, so a large core fills up instead of running on one stack. */
    private static final List<BlockPos> s_fueled = new ArrayList<>();
    /** Active reactors whose steam has nowhere to go, so it is thrown away to keep them cooling. */
    private static final List<BlockPos> s_vented = new ArrayList<>();
    private static final List<Supplier<Boolean>> s_pending = new ArrayList<>();
    private static ServerLevel s_level;

    private Showcase() {
    }

    //endregion
}
