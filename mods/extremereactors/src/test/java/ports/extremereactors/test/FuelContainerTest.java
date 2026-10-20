/*
 * Extreme Reactors 1.19.2 port - tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.test;

import it.zerono.mods.extremereactors.api.internal.InternalDispatcher;
import it.zerono.mods.extremereactors.api.reactor.Reactant;
import it.zerono.mods.extremereactors.api.reactor.ReactantType;
import it.zerono.mods.extremereactors.api.reactor.ReactantsRegistry;
import it.zerono.mods.extremereactors.api.reactor.Reaction;
import it.zerono.mods.extremereactors.api.reactor.ReactionsRegistry;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.FuelContainer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity.SyncReason;
import ports.extremereactors.corelib.data.stack.OperationMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reactor fuel pile: shared fuel and waste capacity, and fuel burning into the waste its reaction names.
 * The reactants and reactions are the ones {@code ReactorGameData} registers.
 */
class FuelContainerTest {

    private static Reactant yellorium, cyanite, blutonium;

    @BeforeAll
    static void registerReactants() {

        InternalDispatcher.runInternal(() -> {

            ReactantsRegistry.register("yellorium", ReactantType.Fuel, 0xc6ba54, "yellorium");
            ReactantsRegistry.registerFuel("blutonium", 0x17179c, 2.23f, 0.6f, 2.0f, 0.0137f, 0.0006f, "blutonium");
            ReactantsRegistry.register("cyanite", ReactantType.Waste, 0x5387b7, "cyanite");
            ReactantsRegistry.register("magentite", ReactantType.Waste, 0xe41de4, "magentite");
            ReactionsRegistry.register("yellorium", "cyanite", Reaction.STANDARD_REACTIVITY, Reaction.STANDARD_FISSIONRATE);
            ReactionsRegistry.register("blutonium", "magentite", 1.0871f, 0.051f);
        });

        yellorium = ReactantsRegistry.get("yellorium").orElseThrow();
        cyanite = ReactantsRegistry.get("cyanite").orElseThrow();
        blutonium = ReactantsRegistry.get("blutonium").orElseThrow();
    }

    private static FuelContainer container(final int capacity) {

        final FuelContainer container = new FuelContainer();

        container.setCapacity(capacity);
        return container;
    }

    @Test
    void fuelAndWasteShareTheCapacity() {

        final FuelContainer pile = container(1000);

        assertEquals(600, pile.insertFuel(yellorium, 600, OperationMode.Execute));
        assertEquals(400, pile.insertWaste(cyanite, 600, OperationMode.Execute));
        assertEquals(0, pile.insertFuel(yellorium, 1, OperationMode.Execute));
        assertEquals(600, pile.getFuelAmount());
        assertEquals(400, pile.getWasteAmount());
    }

    @Test
    void simulatedInsertionsChangeNothing() {

        final FuelContainer pile = container(1000);

        assertEquals(1000, pile.insertFuel(yellorium, 1500, OperationMode.Simulate));
        assertTrue(pile.isEmpty());
    }

    @Test
    void aPileHoldsOneFuelAtATime() {

        final FuelContainer pile = container(1000);

        pile.insertFuel(yellorium, 100, OperationMode.Execute);
        assertEquals(0, pile.insertFuel(blutonium, 100, OperationMode.Execute));
        assertEquals(yellorium, pile.getFuel().orElseThrow());
    }

    @Test
    void burningAccumulatesFractionsBeforeMakingWaste() {

        final FuelContainer pile = container(1000);

        pile.insertFuel(yellorium, 100, OperationMode.Execute);
        pile.onIrradiation(0.4f);
        assertEquals(100, pile.getFuelAmount());
        assertEquals(0, pile.getWasteAmount());

        pile.onIrradiation(0.7f);
        assertEquals(99, pile.getFuelAmount());
        assertEquals(1, pile.getWasteAmount());
        assertEquals(cyanite, pile.getWaste().orElseThrow());

        pile.onIrradiation(2.95f);
        assertEquals(96, pile.getFuelAmount());
        assertEquals(4, pile.getWasteAmount());
    }

    @Test
    void burningNeverUsesMoreFuelThanThePileHolds() {

        final FuelContainer pile = container(1000);

        pile.insertFuel(yellorium, 3, OperationMode.Execute);
        pile.onIrradiation(10f);
        assertEquals(0, pile.getFuelAmount());
        assertEquals(3, pile.getWasteAmount());

        pile.onIrradiation(Float.NaN);
        pile.onIrradiation(Float.POSITIVE_INFINITY);
        assertEquals(3, pile.getWasteAmount());
    }

    @Test
    void reactivityComesFromTheFuelsReaction() {

        final FuelContainer pile = container(1000);

        assertEquals(Reaction.STANDARD_REACTIVITY, pile.getFuelReactivity());
        pile.insertFuel(blutonium, 10, OperationMode.Execute);
        assertEquals(1.0871f, pile.getFuelReactivity());
        assertEquals(2.23f, pile.getFuelProperties().getModerationFactor());
    }

    @Test
    void savedPilesLoadBack() {

        final FuelContainer pile = container(1000);

        pile.insertFuel(yellorium, 250, OperationMode.Execute);
        pile.insertWaste(cyanite, 75, OperationMode.Execute);

        final CompoundTag saved = pile.syncDataTo(new CompoundTag(), SyncReason.FullSync);
        final FuelContainer loaded = container(1000);

        loaded.syncDataFrom(saved, SyncReason.FullSync);
        assertEquals(250, loaded.getFuelAmount());
        assertEquals(75, loaded.getWasteAmount());
        assertEquals(yellorium, loaded.getFuel().orElseThrow());
        assertEquals(cyanite, loaded.getWaste().orElseThrow());
        assertFalse(loaded.isEmpty());
    }
}
