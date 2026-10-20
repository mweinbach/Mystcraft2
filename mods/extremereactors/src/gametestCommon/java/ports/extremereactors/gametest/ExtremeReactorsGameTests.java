/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.zerono.mods.extremereactors.api.reactor.ModeratorsRegistry;
import it.zerono.mods.extremereactors.api.reactor.ReactantType;
import it.zerono.mods.extremereactors.api.turbine.CoilMaterialRegistry;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.config.Worldgen;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.MultiblockReactor;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.OperationalMode;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.ReactantHelper;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.MultiblockTurbine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.material.Fluids;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.compat.computer.IComputerPort;
import ports.extremereactors.corelib.compat.computer.MultiblockComputerPeripheral;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.multiblock.AbstractMultiblockController;
import ports.extremereactors.multiblock.AbstractMultiblockPart;
import ports.extremereactors.platform.Platform;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The game tests shared by both loaders. The loader entry classes only forward to these methods.
 */
public final class ExtremeReactorsGameTests {

    /** An empty 16x16x16 template, under {@code data/bigreactors/structures}. */
    public static final String EMPTY_TEMPLATE = "gametest/empty16";

    public static final int SHORT_TIMEOUT = 200;
    public static final int SIMULATION_TIMEOUT = 600;
    public static final int BOILING_TIMEOUT = 2400;

    private static final int SIMULATION_TICKS = 200;

    private ExtremeReactorsGameTests() {
    }

    //region multiblock assembly

    public static void basicReactorAssembles(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);

        basicReactor(origin).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> {

                    final MultiblockReactor reactor = assembledReactor(helper, origin.offset(0, 1, 1));

                    check(1 == reactor.getFuelRodsCount(), "expected one fuel rod, got " + reactor.getFuelRodsCount());
                    check(1 == reactor.getControlRodsCount(), "expected one control rod, got " + reactor.getControlRodsCount());
                    check(1 == reactor.getPowerTapsCount(), "expected one power tap, got " + reactor.getPowerTapsCount());
                    check(OperationalMode.Passive == reactor.getOperationalMode(), "a reactor without fluid ports must be passive");
                })
                .thenSucceed();
    }

    public static void reactorWithoutControllerDoesNotAssemble(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);

        basicReactor(origin)
                .set(0, 1, 1, Content.Blocks.REACTOR_CASING_BASIC)
                .place(helper);

        expectValidationError(helper, origin, "multiblock.validation.reactor.too_few_controllers");
    }

    public static void reactorFuelRodNeedsCasingBase(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);

        basicReactor(origin)
                .set(1, 0, 1, Content.Blocks.REACTOR_GLASS_BASIC)
                .place(helper);

        expectValidationError(helper, origin, "multiblock.validation.reactor.invalid_base_for_fuel_assembly");
    }

    public static void basicTurbineAssembles(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);

        basicTurbine(origin, 1, 3).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> {

                    final MultiblockTurbine turbine = assembledTurbine(helper, origin.offset(0, 2, 2));

                    check(4 == turbine.getRotorBladesCount(), "expected four rotor blades, got " + turbine.getRotorBladesCount());
                })
                .thenSucceed();
    }

    public static void turbineLooseBladeDoesNotAssemble(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);

        basicTurbine(origin, 1, 3)
                .set(1, 2, 1, Content.Blocks.TURBINE_ROTORBLADE_BASIC)
                .place(helper);

        expectValidationError(helper, origin, "multiblock.validation.turbine.found_loose_rotor_blades");
    }

    /**
     * ER2 1.19.2 clears its cache of coil positions right before walking the rotor, so the coils-after-blades rule
     * is never enforced. This pins that upstream behaviour.
     */
    public static void turbineCoilsBeforeBladesAssembleLikeUpstream(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);

        basicTurbine(origin, 3, 1).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> assembledTurbine(helper, origin.offset(0, 2, 2)))
                .thenSucceed();
    }

    //endregion
    //region reactor simulation

    public static void passiveReactorBurnsYellorium(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);
        final BlockPos controller = origin.offset(0, 1, 1);

        basicReactor(origin).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> assembledReactor(helper, controller))
                .thenExecute(() -> {

                    insertFuel(helper, origin.offset(1, 1, 0), Direction.NORTH, 16);
                    assembledReactor(helper, controller).setMachineActive(true);
                })
                .thenWaitUntil(() -> check(assembledReactor(helper, controller).getFuelAmount() > 0,
                        "the reactor did not take any fuel from the access port"))
                .thenIdle(SIMULATION_TICKS)
                .thenExecute(() -> {

                    final MultiblockReactor reactor = assembledReactor(helper, controller);
                    final double energy = storedEnergy(reactor);
                    final float heat = (float) reactor.getFuelHeatValue().getAsDouble();

                    check(energy > 0, "the reactor generated no energy");
                    check(reactor.getUiStats().getFuelConsumedLastTick() > 0, "the reactor burned no fuel");
                    check(heat > 20, "the fuel did not heat up: " + heat);
                })
                .thenWaitUntil(() -> check(assembledReactor(helper, controller).getWasteAmount() > 0,
                        "the reactor produced no cyanite"))
                .thenSucceed();
    }

    public static void controlRodsReduceOutput(final GameTestHelper helper) {

        final BlockPos withdrawn = new BlockPos(1, 1, 1);
        final BlockPos inserted = new BlockPos(6, 1, 1);

        basicReactor(withdrawn).place(helper);
        basicReactor(inserted).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> {

                    assembledReactor(helper, withdrawn.offset(0, 1, 1));
                    assembledReactor(helper, inserted.offset(0, 1, 1));
                })
                .thenExecute(() -> {

                    startReactor(helper, withdrawn, 0);
                    startReactor(helper, inserted, 100);
                })
                .thenIdle(SIMULATION_TICKS)
                .thenExecute(() -> {

                    final double withdrawnEnergy = storedEnergy(assembledReactor(helper, withdrawn.offset(0, 1, 1)));
                    final double insertedEnergy = storedEnergy(assembledReactor(helper, inserted.offset(0, 1, 1)));

                    check(withdrawnEnergy > 0, "the reactor with withdrawn control rods generated no energy");
                    check(insertedEnergy < withdrawnEnergy, "inserting the control rods did not reduce the output: "
                            + insertedEnergy + " >= " + withdrawnEnergy);
                })
                .thenSucceed();
    }

    public static void graphiteModeratesBetterThanAir(final GameTestHelper helper) {

        final BlockPos air = new BlockPos(1, 1, 1);
        final BlockPos graphite = new BlockPos(8, 1, 1);

        moderatedReactor(air, Blocks.AIR).place(helper);
        moderatedReactor(graphite, Content.Blocks.GRAPHITE_BLOCK.get()).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> {

                    assembledReactor(helper, air.offset(0, 2, 2));
                    assembledReactor(helper, graphite.offset(0, 2, 2));
                })
                .thenExecute(() -> {

                    insertFuel(helper, air.offset(2, 2, 0), Direction.NORTH, 32);
                    insertFuel(helper, graphite.offset(2, 2, 0), Direction.NORTH, 32);
                    assembledReactor(helper, air.offset(0, 2, 2)).setMachineActive(true);
                    assembledReactor(helper, graphite.offset(0, 2, 2)).setMachineActive(true);
                })
                .thenIdle(SIMULATION_TICKS)
                .thenExecute(() -> {

                    final double airEnergy = storedEnergy(assembledReactor(helper, air.offset(0, 2, 2)));
                    final double graphiteEnergy = storedEnergy(assembledReactor(helper, graphite.offset(0, 2, 2)));

                    check(airEnergy > 0, "the air-moderated reactor generated no energy");
                    check(graphiteEnergy > airEnergy, "graphite did not outperform air: "
                            + graphiteEnergy + " <= " + airEnergy);
                })
                .thenSucceed();
    }

    public static void activeReactorBoilsWater(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);
        final BlockPos controller = origin.offset(0, 1, 1);

        reinforcedActiveReactor(origin).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> check(OperationalMode.Active == assembledReactor(helper, controller).getOperationalMode(),
                        "a reactor with a fluid port must be active"))
                .thenExecute(() -> {

                    final MultiblockReactor reactor = assembledReactor(helper, controller);
                    final IFluidHandler input = reactor.getFluidHandler(IoDirection.Input)
                            .orElseThrow(() -> new GameTestAssertException("the active reactor has no coolant input"));
                    final int filled = input.fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);

                    check(filled > 0, "the reactor did not accept any water");
                    insertFuel(helper, origin.offset(1, 1, 0), Direction.NORTH, 64);
                    reactor.setMachineActive(true);
                })
                .thenWaitUntil(() -> {

                    final MultiblockReactor reactor = assembledReactor(helper, controller);

                    check(reactor.getVaporAmount() > 0, "no steam yet; reactor heat is "
                            + reactor.getReactorHeatValue().getAsDouble() + " C, coolant " + reactor.getCoolantAmount() + " mB");
                })
                .thenSucceed();
    }

    //endregion
    //region turbine simulation

    public static void turbineSpinsOnSteam(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);
        final BlockPos controller = origin.offset(0, 2, 2);

        basicTurbine(origin, 1, 3)
                .set(2, 2, 0, Content.Blocks.TURBINE_CREATIVE_STEAM_GENERATOR_BASIC)
                .place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> assembledTurbine(helper, controller))
                .thenExecute(() -> {

                    final MultiblockTurbine turbine = assembledTurbine(helper, controller);

                    turbine.setInductorEngaged(true);
                    turbine.setMachineActive(true);
                })
                .thenWaitUntil(() -> {

                    final MultiblockTurbine turbine = assembledTurbine(helper, controller);

                    check(turbine.getRotorSpeed() > 0, "the rotor is not spinning");
                    check(turbine.getFluidConsumedLastTick() > 0, "the turbine consumed no steam");
                    check(storedEnergy(turbine) > 0, "the turbine generated no energy at "
                            + turbine.getRotorSpeed() + " RPM");
                })
                .thenSucceed();
    }

    /**
     * Clients learn the rotor speed of an assembled turbine only from block entity data packets, so the idle turbine
     * plays the client: it must take the running turbine's speed from such a packet.
     */
    public static void dataPacketsUpdateAnAssembledTurbine(final GameTestHelper helper) {

        final BlockPos runningOrigin = new BlockPos(1, 1, 1);
        final BlockPos idleOrigin = new BlockPos(8, 1, 1);
        final BlockPos runningController = runningOrigin.offset(0, 2, 2);
        final BlockPos idleController = idleOrigin.offset(0, 2, 2);

        basicTurbine(runningOrigin, 1, 3)
                .set(2, 2, 0, Content.Blocks.TURBINE_CREATIVE_STEAM_GENERATOR_BASIC)
                .place(helper);
        basicTurbine(idleOrigin, 1, 3).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> {

                    assembledTurbine(helper, runningController);
                    assembledTurbine(helper, idleController);
                })
                .thenExecute(() -> {

                    final MultiblockTurbine turbine = assembledTurbine(helper, runningController);

                    turbine.setInductorEngaged(true);
                    turbine.setMachineActive(true);
                })
                .thenWaitUntil(() -> check(assembledTurbine(helper, runningController).getRotorSpeed() > 0,
                        "the rotor is not spinning"))
                .thenExecute(() -> {

                    final float speed = assembledTurbine(helper, runningController).getRotorSpeed();
                    final Packet<?> packet = saveDelegate(helper, runningOrigin, 5).getUpdatePacket();

                    check(packet instanceof ClientboundBlockEntityDataPacket, "no block entity data packet");
                    check(0 == assembledTurbine(helper, idleController).getRotorSpeed(), "the idle rotor is spinning");

                    saveDelegate(helper, idleOrigin, 5).load(((ClientboundBlockEntityDataPacket) packet).getTag());

                    final float synced = assembledTurbine(helper, idleController).getRotorSpeed();

                    check(speed == synced, "the data packet carried " + synced + " RPM instead of " + speed);
                })
                .thenSucceed();
    }

    //endregion
    //region loader interop

    public static void reactorItemsAndEnergyThroughLoaderApis(final GameTestHelper helper, final LoaderApis apis) {

        final BlockPos origin = new BlockPos(1, 1, 1);
        final BlockPos controller = origin.offset(0, 1, 1);
        final BlockPos accessPort = helper.absolutePos(origin.offset(1, 1, 0));
        final BlockPos powerTap = helper.absolutePos(origin.offset(2, 1, 1));
        final ServerLevel level = helper.getLevel();

        basicReactor(origin).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> assembledReactor(helper, controller))
                .thenExecute(() -> {

                    final long dirt = apis.insertItems(level, accessPort, Direction.NORTH, new ItemStack(Items.DIRT, 4));
                    final long fuel = apis.insertItems(level, accessPort, Direction.NORTH,
                            new ItemStack(Content.Items.YELLORIUM_INGOT.get(), 16));

                    check(0 == dirt, "the access port accepted " + dirt + " dirt");
                    check(16 == fuel, "the access port accepted " + fuel + " of 16 yellorium ingots");
                    assembledReactor(helper, controller).setMachineActive(true);
                })
                .thenWaitUntil(() -> check(storedEnergy(assembledReactor(helper, controller)) > 1000,
                        "the reactor has not generated 1000 FE yet"))
                .thenExecute(() -> {

                    final MultiblockReactor reactor = assembledReactor(helper, controller);
                    final double before = storedEnergy(reactor);
                    final long extracted = apis.extractEnergy(level, powerTap, Direction.EAST, 500);
                    final double after = storedEnergy(reactor);

                    check(500 == extracted, "extracted " + extracted + " of 500 FE from the power tap");
                    check(Math.abs(before - after - extracted) < 1e-6, "the reactor buffer went from "
                            + before + " to " + after + " FE after extracting " + extracted);
                })
                .thenSucceed();
    }

    public static void reactorCoolantThroughLoaderApis(final GameTestHelper helper, final LoaderApis apis) {

        final BlockPos origin = new BlockPos(1, 1, 1);
        final BlockPos controller = origin.offset(0, 1, 1);
        final BlockPos fluidPort = helper.absolutePos(origin.offset(1, 1, 2));

        reinforcedActiveReactor(origin).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> assembledReactor(helper, controller))
                .thenExecute(() -> {

                    final long lava = apis.insertFluid(helper.getLevel(), fluidPort, Direction.SOUTH, Fluids.LAVA, 1000);
                    final long water = apis.insertFluid(helper.getLevel(), fluidPort, Direction.SOUTH, Fluids.WATER, 1000);
                    final int coolant = assembledReactor(helper, controller).getCoolantAmount();

                    check(0 == lava, "the fluid port accepted " + lava + " mB of lava");
                    check(1000 == water, "the fluid port accepted " + water + " of 1000 mB of water");
                    check(1000 == coolant, "the reactor holds " + coolant + " mB of coolant");
                })
                .thenSucceed();
    }

    //endregion
    //region computer ports

    /**
     * Drives a reactor through its computer port the way a ComputerCraft script does: numbers arrive as doubles,
     * tables as maps keyed by doubles, and bad arguments raise errors.
     */
    public static void computerPortControlsTheReactor(final GameTestHelper helper) {

        final BlockPos origin = new BlockPos(1, 1, 1);
        final BlockPos controller = origin.offset(0, 1, 1);
        final BlockPos computerPort = origin.offset(2, 1, 1);

        reinforcedComputerReactor(origin).place(helper);

        helper.startSequence()
                .thenWaitUntil(() -> assembledReactor(helper, controller))
                .thenExecute(() -> {

                    final MultiblockComputerPeripheral<?, ?> peripheral = computerPeripheral(helper, computerPort);
                    final MultiblockReactor reactor = assembledReactor(helper, controller);

                    check("extremereactor-reactorComputerPort".equals(peripheral.getPeripheralStaticName()),
                            "unexpected peripheral type " + peripheral.getPeripheralStaticName());
                    check(Boolean.TRUE.equals(peripheral.invoke("getConnected", new Object[0])), "getConnected() is not true");
                    check(Integer.valueOf(1).equals(peripheral.invoke("getNumberOfControlRods", new Object[0])),
                            "getNumberOfControlRods() is not 1");
                    check(Map.of("x", helper.absolutePos(origin).getX(), "y", helper.absolutePos(origin).getY(),
                                    "z", helper.absolutePos(origin).getZ())
                                    .equals(peripheral.invoke("getMinimumCoordinate", new Object[0])),
                            "getMinimumCoordinate() is not the reactor's corner");

                    insertFuel(helper, origin.offset(1, 1, 0), Direction.NORTH, 16);
                    peripheral.invoke("setAllControlRodLevels", new Object[]{40.0});
                    check(40 == reactor.getControlRodByIndex(0).map(rod -> (int) rod.getInsertionRatio()).orElse(-1),
                            "setAllControlRodLevels(40) did not move the control rod");

                    peripheral.invoke("setControlRodsLevels", new Object[]{Map.of(0.0, 25.0)});
                    check(Map.of(0, 25).equals(peripheral.invoke("getControlRodsLevels", new Object[0])),
                            "setControlRodsLevels({[0]=25}) did not move the control rod");

                    peripheral.invoke("setControlRodName", new Object[]{0.0, "core"});
                    check("core".equals(peripheral.invoke("getControlRodName", new Object[]{0.0})),
                            "the control rod was not renamed");

                    expectComputerError(() -> peripheral.invoke("setControlRodLevel", new Object[]{0.0, 101.0}),
                            "setControlRodLevel(0, 101)");
                    expectComputerError(() -> peripheral.invoke("setActive", new Object[]{"yes"}), "setActive(\"yes\")");
                    expectComputerError(() -> peripheral.invoke("noSuchMethod", new Object[0]), "noSuchMethod()");

                    peripheral.invoke("setActive", new Object[]{true});
                    check(Boolean.TRUE.equals(peripheral.invoke("getActive", new Object[0])), "setActive(true) did not start the reactor");
                })
                .thenWaitUntil(() -> {

                    final Object stats = computerPeripheral(helper, computerPort).invoke("getFuelStats", new Object[0]);

                    check(stats instanceof Map<?, ?> map && map.get("fuelAmount") instanceof Number amount
                                    && amount.doubleValue() > 0,
                            "getFuelStats() reports no fuel: " + stats);
                })
                .thenExecute(() -> helper.setBlock(controller, Content.Blocks.REACTOR_CASING_REINFORCED.get()))
                .thenWaitUntil(() -> {

                    final MultiblockComputerPeripheral<?, ?> peripheral = computerPeripheral(helper, computerPort);

                    check(Boolean.FALSE.equals(peripheral.invoke("getConnected", new Object[0])),
                            "getConnected() is still true without a controller");
                    expectComputerError(() -> peripheral.invoke("getActive", new Object[0]), "getActive() on a broken reactor");
                })
                .thenSucceed();
    }

    private static MultiblockComputerPeripheral<?, ?> computerPeripheral(final GameTestHelper helper, final BlockPos position) {

        if (blockEntity(helper, position) instanceof IComputerPort port) {
            return port.getComputerPeripheral();
        }

        throw new GameTestAssertException("no computer port at " + position);
    }

    private static void expectComputerError(final Runnable call, final String what) {

        try {
            call.run();
        } catch (final IllegalArgumentException | IllegalStateException expected) {
            return;
        }

        throw new GameTestAssertException(what + " did not raise an error");
    }

    //endregion
    //region data

    public static void recipeConditionsAndTags(final GameTestHelper helper) {

        final RecipeManager recipes = helper.getLevel().getRecipeManager();

        check(recipes.byKey(id("reactor/reinforced/controlrod_alt")).isPresent(),
                "the steel-free reinforced control rod recipe should load when no steel exists");
        check(recipes.byKey(id("reactor/reinforced/controlrod")).isEmpty(),
                "the steel reinforced control rod recipe should not load without steel");
        check(recipes.byKey(id("smelting/graphite_from_coal")).isPresent(),
                "the config-gated graphite smelting recipe should load by default");
        check(recipes.byKey(id("misc/book/erguide")).isEmpty(),
                "the Patchouli book recipe should not load without Patchouli");

        final ItemStack yellorium = new ItemStack(Content.Items.YELLORIUM_INGOT.get());

        check(yellorium.is(itemTag("c", "yellorium_ingots")), "yellorium is not in c:yellorium_ingots");
        check(yellorium.is(itemTag("forge", "ingots/yellorium")), "yellorium is not in forge:ingots/yellorium");
        check(new ItemStack(Content.Items.CYANITE_INGOT.get()).is(itemTag("c", "cyanite_ingots")),
                "cyanite is not in c:cyanite_ingots");
        check(ReactantHelper.isValidSource(ReactantType.Fuel, yellorium), "yellorium is not a valid reactor fuel");

        check(CoilMaterialRegistry.get(Blocks.IRON_BLOCK.defaultBlockState()).isPresent(),
                "the iron block is not a turbine coil");
        check(ModeratorsRegistry.getFrom(Content.Blocks.GRAPHITE_BLOCK.get().defaultBlockState()).isPresent(),
                "the graphite block is not a reactor moderator");

        helper.succeed();
    }

    //endregion
    //region world generation

    /** The heights upstream's placed features decode to on 1.19.2. */
    public static void oreFeaturesPlaceAtUpstreamHeights(final GameTestHelper helper) {

        final Registry<PlacedFeature> placed = helper.getLevel().registryAccess()
                .registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);

        checkOreHeights(placed, "yellorite_ore", 0, 32);
        checkOreHeights(placed, "yellorite_deepslate_ore", -64, -48);
        checkOreHeights(placed, "anglesite_ore", 5, 200);
        checkOreHeights(placed, "benitoite_ore", 3, 126);
        helper.succeed();
    }

    public static void oreGenerationFollowsConfig(final GameTestHelper helper) {

        final Worldgen worldgen = Config.COMMON.worldgen;

        checkOreInBiome(helper, Biomes.PLAINS, "yellorite_ore", worldgen.generatesYellorite());
        checkOreInBiome(helper, Biomes.PLAINS, "yellorite_deepslate_ore", worldgen.generatesYellorite());
        checkOreInBiome(helper, Biomes.END_HIGHLANDS, "anglesite_ore", worldgen.generatesAnglesite());
        checkOreInBiome(helper, Biomes.NETHER_WASTES, "benitoite_ore", worldgen.generatesBenitoite());
        checkOreInBiome(helper, Biomes.PLAINS, "benitoite_ore", false);
        helper.succeed();
    }

    private static void checkOreHeights(final Registry<PlacedFeature> placed, final String name,
                                        final int minY, final int maxY) {

        final PlacedFeature feature = placed.getOptional(id(name))
                .orElseThrow(() -> new GameTestAssertException("missing placed feature " + name));
        final JsonObject height = feature.placement().stream()
                .map(modifier -> PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, modifier)
                        .getOrThrow(false, message -> {}).getAsJsonObject())
                .filter(json -> "minecraft:height_range".equals(json.get("type").getAsString()))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException(name + " has no height range"))
                .getAsJsonObject("height");

        checkAbsoluteAnchor(name + " minimum", height.getAsJsonObject("min_inclusive"), minY);
        checkAbsoluteAnchor(name + " maximum", height.getAsJsonObject("max_inclusive"), maxY);
    }

    private static void checkAbsoluteAnchor(final String what, final JsonObject anchor, final int y) {

        final JsonObject expected = new JsonObject();

        expected.addProperty("absolute", y);
        check(expected.equals(anchor), what + ": expected " + expected + ", got " + anchor);
    }

    private static void checkOreInBiome(final GameTestHelper helper, final ResourceKey<Biome> biomeKey,
                                        final String name, final boolean expected) {

        final ResourceKey<PlacedFeature> featureKey = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, id(name));
        final Biome biome = helper.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)
                .getOrThrow(biomeKey);
        final boolean present = biome.getGenerationSettings().features().stream()
                .flatMap(HolderSet::stream)
                .anyMatch(holder -> holder.is(featureKey));

        check(expected == present, name + (present ? " generates" : " does not generate") + " in " +
                biomeKey.location() + ", expected the opposite");
    }

    //endregion
    //region structures

    /** A 3x3x3 basic reactor with a single fuel column. */
    private static Structure basicReactor(final BlockPos origin) {
        return new Structure(origin, 3, 3, 3, Content.Blocks.REACTOR_CASING_BASIC.get())
                .set(1, 2, 1, Content.Blocks.REACTOR_CONTROLROD_BASIC)
                .set(1, 1, 1, Content.Blocks.REACTOR_FUELROD_BASIC)
                .set(0, 1, 1, Content.Blocks.REACTOR_CONTROLLER_BASIC)
                .set(2, 1, 1, Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_BASIC)
                .set(1, 1, 0, Content.Blocks.REACTOR_SOLID_ACCESSPORT_BASIC)
                .set(1, 1, 2, Content.Blocks.REACTOR_GLASS_BASIC);
    }

    /** A 3x3x3 reinforced reactor whose fluid port makes it an active (coolant) reactor. */
    private static Structure reinforcedActiveReactor(final BlockPos origin) {
        return new Structure(origin, 3, 3, 3, Content.Blocks.REACTOR_CASING_REINFORCED.get())
                .set(1, 2, 1, Content.Blocks.REACTOR_CONTROLROD_REINFORCED)
                .set(1, 1, 1, Content.Blocks.REACTOR_FUELROD_REINFORCED)
                .set(0, 1, 1, Content.Blocks.REACTOR_CONTROLLER_REINFORCED)
                .set(1, 1, 0, Content.Blocks.REACTOR_SOLID_ACCESSPORT_REINFORCED)
                .set(1, 1, 2, Content.Blocks.REACTOR_FLUIDPORT_FORGE_PASSIVE_REINFORCED);
    }

    /** A 3x3x3 passive reinforced reactor with a computer port. */
    private static Structure reinforcedComputerReactor(final BlockPos origin) {
        return new Structure(origin, 3, 3, 3, Content.Blocks.REACTOR_CASING_REINFORCED.get())
                .set(1, 2, 1, Content.Blocks.REACTOR_CONTROLROD_REINFORCED)
                .set(1, 1, 1, Content.Blocks.REACTOR_FUELROD_REINFORCED)
                .set(0, 1, 1, Content.Blocks.REACTOR_CONTROLLER_REINFORCED)
                .set(1, 1, 0, Content.Blocks.REACTOR_SOLID_ACCESSPORT_REINFORCED)
                .set(1, 1, 2, Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_REINFORCED)
                .set(2, 1, 1, Content.Blocks.REACTOR_COMPUTERPORT_REINFORCED);
    }

    /** A 5x5x5 basic reactor with a three rods column and the given block in every other interior position. */
    private static Structure moderatedReactor(final BlockPos origin, final Block moderator) {

        final Structure structure = new Structure(origin, 5, 5, 5, Content.Blocks.REACTOR_CASING_BASIC.get());

        for (int x = 1; x < 4; ++x) {
            for (int y = 1; y < 4; ++y) {
                for (int z = 1; z < 4; ++z) {
                    structure.set(x, y, z, moderator);
                }
            }
        }

        return structure
                .set(2, 4, 2, Content.Blocks.REACTOR_CONTROLROD_BASIC)
                .set(2, 1, 2, Content.Blocks.REACTOR_FUELROD_BASIC)
                .set(2, 2, 2, Content.Blocks.REACTOR_FUELROD_BASIC)
                .set(2, 3, 2, Content.Blocks.REACTOR_FUELROD_BASIC)
                .set(0, 2, 2, Content.Blocks.REACTOR_CONTROLLER_BASIC)
                .set(4, 2, 2, Content.Blocks.REACTOR_POWERTAP_FE_PASSIVE_BASIC)
                .set(2, 2, 0, Content.Blocks.REACTOR_SOLID_ACCESSPORT_BASIC);
    }

    /**
     * A 5x5x5 basic turbine with a vertical rotor, four blades at {@code bladesY} and a ring of iron coils at
     * {@code coilsY}.
     */
    private static Structure basicTurbine(final BlockPos origin, final int bladesY, final int coilsY) {

        final Structure structure = new Structure(origin, 5, 5, 5, Content.Blocks.TURBINE_CASING_BASIC.get())
                .set(2, 0, 2, Content.Blocks.TURBINE_ROTORBEARING_BASIC)
                .set(0, 2, 2, Content.Blocks.TURBINE_CONTROLLER_BASIC)
                .set(4, 2, 2, Content.Blocks.TURBINE_POWERTAP_FE_PASSIVE_BASIC);

        for (int y = 1; y < 4; ++y) {
            structure.set(2, y, 2, Content.Blocks.TURBINE_ROTORSHAFT_BASIC);
        }

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            structure.set(2 + direction.getStepX(), bladesY, 2 + direction.getStepZ(), Content.Blocks.TURBINE_ROTORBLADE_BASIC);
        }

        for (int x = 1; x < 4; ++x) {
            for (int z = 1; z < 4; ++z) {
                if (x != 2 || z != 2) {
                    structure.set(x, coilsY, z, Blocks.IRON_BLOCK);
                }
            }
        }

        return structure;
    }

    /** A cuboid with a shell of one block and an air interior, plus per-position overrides. */
    private static final class Structure {

        Structure(final BlockPos origin, final int sizeX, final int sizeY, final int sizeZ, final Block shell) {

            this._origin = origin;
            this._sizeX = sizeX;
            this._sizeY = sizeY;
            this._sizeZ = sizeZ;
            this._shell = shell;
            this._overrides = new HashMap<>();
        }

        Structure set(final int x, final int y, final int z, final Supplier<? extends Block> block) {
            return this.set(x, y, z, block.get());
        }

        Structure set(final int x, final int y, final int z, final Block block) {

            this._overrides.put(new BlockPos(x, y, z), block);
            return this;
        }

        void place(final GameTestHelper helper) {

            for (int x = 0; x < this._sizeX; ++x) {
                for (int y = 0; y < this._sizeY; ++y) {
                    for (int z = 0; z < this._sizeZ; ++z) {

                        final BlockPos relative = new BlockPos(x, y, z);
                        final boolean onShell = 0 == x || 0 == y || 0 == z ||
                                this._sizeX - 1 == x || this._sizeY - 1 == y || this._sizeZ - 1 == z;
                        final Block block = this._overrides.getOrDefault(relative, onShell ? this._shell : Blocks.AIR);

                        helper.setBlock(this._origin.offset(relative), block);
                    }
                }
            }
        }

        private final BlockPos _origin;
        private final int _sizeX;
        private final int _sizeY;
        private final int _sizeZ;
        private final Block _shell;
        private final Map<BlockPos, Block> _overrides;
    }

    //endregion
    //region helpers

    private static void startReactor(final GameTestHelper helper, final BlockPos reactorOrigin, final int insertion) {

        final MultiblockReactor reactor = assembledReactor(helper, reactorOrigin.offset(0, 1, 1));

        insertFuel(helper, reactorOrigin.offset(1, 1, 0), Direction.NORTH, 16);
        reactor.setControlRodsInsertionRatio(insertion);
        reactor.setMachineActive(true);
    }

    private static void insertFuel(final GameTestHelper helper, final BlockPos accessPort, final Direction side,
                                   final int ingots) {

        final BlockEntity port = blockEntity(helper, accessPort);
        final IItemHandler handler = CapabilityHelper.getCapability(port, CapabilityHelper.ITEM_HANDLER, side)
                .resolve()
                .orElseThrow(() -> new GameTestAssertException("the access port exposes no item handler"));
        final ItemStack fuel = new ItemStack(Content.Items.YELLORIUM_INGOT.get(), ingots);

        for (int slot = 0; slot < handler.getSlots(); ++slot) {

            final ItemStack remainder = handler.insertItem(slot, fuel.copy(), false);

            if (remainder.getCount() < ingots) {
                return;
            }
        }

        throw new GameTestAssertException("the access port refused yellorium ingots");
    }

    private static void expectValidationError(final GameTestHelper helper, final BlockPos partPosition,
                                              final String expectedKey) {
        helper.startSequence()
                .thenWaitUntil(() -> {

                    final AbstractMultiblockController<?> controller = controllerAt(helper, partPosition)
                            .orElseThrow(() -> new GameTestAssertException("the part has no controller yet"));
                    final String key = controller.getLastError()
                            .map(error -> error.getChatMessage().getContents())
                            .filter(TranslatableContents.class::isInstance)
                            .map(contents -> ((TranslatableContents) contents).getKey())
                            .orElse("<none>");

                    check(expectedKey.equals(key), "expected validation error " + expectedKey + ", got " + key);
                    check(!controller.isAssembled(), "the invalid machine assembled");
                })
                .thenSucceed();
    }

    private static MultiblockReactor assembledReactor(final GameTestHelper helper, final BlockPos partPosition) {
        return assembled(helper, partPosition, MultiblockReactor.class);
    }

    private static MultiblockTurbine assembledTurbine(final GameTestHelper helper, final BlockPos partPosition) {
        return assembled(helper, partPosition, MultiblockTurbine.class);
    }

    private static <C extends AbstractMultiblockController<?>> C assembled(final GameTestHelper helper,
                                                                     final BlockPos partPosition,
                                                                     final Class<C> type) {

        final AbstractMultiblockController<?> controller = controllerAt(helper, partPosition)
                .orElseThrow(() -> new GameTestAssertException("the part at " + partPosition + " has no controller yet"));

        if (!type.isInstance(controller)) {
            throw new GameTestAssertException("expected a " + type.getSimpleName() + ", got " + controller);
        }

        if (!controller.isAssembled()) {
            throw new GameTestAssertException("the machine is not assembled: " + controller.getLastError()
                    .map(error -> error.getChatMessage().getString())
                    .orElse("no validation error"));
        }

        return type.cast(controller);
    }

    private static Optional<AbstractMultiblockController<?>> controllerAt(final GameTestHelper helper, final BlockPos position) {

        if (blockEntity(helper, position) instanceof AbstractMultiblockPart<?> part) {
            return part.getMultiblockController()
                    .filter(AbstractMultiblockController.class::isInstance)
                    .map(controller -> (AbstractMultiblockController<?>) controller);
        }

        throw new GameTestAssertException("no multiblock part at " + position);
    }

    private static AbstractMultiblockPart<?> saveDelegate(final GameTestHelper helper, final BlockPos origin,
                                                          final int size) {

        for (final BlockPos position : BlockPos.betweenClosed(origin, origin.offset(size - 1, size - 1, size - 1))) {

            if (helper.getBlockEntity(position) instanceof AbstractMultiblockPart<?> part
                    && part.isMultiblockSaveDelegate()) {
                return part;
            }
        }

        throw new GameTestAssertException("the machine at " + origin + " has no save delegate");
    }

    private static BlockEntity blockEntity(final GameTestHelper helper, final BlockPos position) {

        final BlockEntity blockEntity = helper.getBlockEntity(position);

        if (null == blockEntity) {
            throw new GameTestAssertException("no block entity at " + position);
        }

        return blockEntity;
    }

    private static double storedEnergy(final MultiblockReactor reactor) {
        return reactor.getEnergyStored(EnergySystem.ForgeEnergy).doubleValue();
    }

    private static double storedEnergy(final MultiblockTurbine turbine) {
        return turbine.getEnergyStored(EnergySystem.ForgeEnergy).doubleValue();
    }

    private static ResourceLocation id(final String path) {
        return new ResourceLocation(Platform.MOD_ID, path);
    }

    private static TagKey<Item> itemTag(final String namespace, final String path) {
        return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(namespace, path));
    }

    private static void check(final boolean condition, final String message) {

        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    //endregion
}
