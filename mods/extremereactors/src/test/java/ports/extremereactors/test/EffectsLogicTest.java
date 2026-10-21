/*
 * Extreme Reactors 1.19.2 port - tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.test;

import org.junit.jupiter.api.Test;
import ports.extremereactors.effects.EffectThresholds;
import ports.extremereactors.effects.ReactorAlarm;
import ports.extremereactors.effects.RodDrive;
import ports.extremereactors.effects.ScramDetector;
import ports.extremereactors.effects.StatusSyncTracker;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client-side effects logic: rod drive motion, SCRAM recognition, alarms, and the server's rate-limited
 * status sync.
 */
class EffectsLogicTest {

    @Test
    void rodDriveStrokesAtFixedSpeed() {

        final RodDrive drive = new RodDrive(0f);

        drive.tick(1f, false);
        assertEquals(RodDrive.STROKE_PER_TICK, drive.get(), 1e-6);
        assertEquals(RodDrive.STROKE_PER_TICK / 2, drive.get(0.5f), 1e-6);

        for (int i = 0; i < 30; ++i) {
            drive.tick(1f, false);
        }

        assertEquals(1f, drive.get(), 1e-6);
        drive.tick(1f, false);
        assertFalse(drive.isMoving());

        drive.tick(0.98f, false);
        assertEquals(0.98f, drive.get(), 1e-6);
    }

    @Test
    void rodDriveDropsFasterThanItStrokesAndSettles() {

        final RodDrive dropping = new RodDrive(0f);
        final RodDrive stroking = new RodDrive(0f);
        int ticksToDrop = 0;

        while (dropping.get() < 1f) {

            dropping.tick(1f, true);
            stroking.tick(1f, false);
            ++ticksToDrop;
        }

        assertTrue(ticksToDrop < 1f / RodDrive.STROKE_PER_TICK, "a SCRAM drop beats a normal stroke");
        assertTrue(stroking.get() < 1f);

        float lowest = 1f;

        for (int i = 0; i < 60; ++i) {

            dropping.tick(1f, true);
            lowest = Math.min(lowest, dropping.get());
            assertTrue(dropping.get() <= 1f, "a bouncing rod never goes past full insertion");
        }

        assertTrue(lowest < 1f, "the rod bounces off the bottom");
        assertEquals(1f, dropping.get(), 1e-6);
        assertFalse(dropping.isMoving());
    }

    @Test
    void scramNeedsShutdownAndFullInsertionTogether() {

        final ScramDetector detector = new ScramDetector();

        assertFalse(detector.update(true, false), "the first update only learns the state");
        assertFalse(detector.update(false, false), "shutting down alone is not a SCRAM");

        for (int i = 0; i < ScramDetector.WINDOW + 5; ++i) {
            assertFalse(detector.update(false, false));
        }

        assertFalse(detector.update(false, true), "inserting the rods long after the shutdown is not a SCRAM");

        final ScramDetector scram = new ScramDetector();

        scram.update(true, false);
        assertFalse(scram.update(true, true), "inserting the rods alone is not a SCRAM");
        assertFalse(scram.update(true, false));
        assertFalse(scram.update(false, false));
        assertTrue(scram.update(false, true), "shutdown then full insertion within the window");
        assertFalse(scram.update(false, true), "a SCRAM is reported once");
    }

    @Test
    void scramIsRecognizedWhicheverUpdateArrivesFirst() {

        final ScramDetector rodsFirst = new ScramDetector();

        rodsFirst.update(true, false);
        assertFalse(rodsFirst.update(true, true));
        assertTrue(rodsFirst.update(false, true));

        final ScramDetector sameTick = new ScramDetector();

        sameTick.update(true, false);
        assertTrue(sameTick.update(false, true));

        final ScramDetector alreadyInserted = new ScramDetector();

        alreadyInserted.update(true, true);
        assertFalse(alreadyInserted.update(false, true), "turning off a reactor whose rods are all in");
    }

    @Test
    void alarmsFollowTheReadings() {

        assertEquals(EnumSet.noneOf(ReactorAlarm.class), alarms(true, true, 500, 0.8, 1000, 0, 4000));
        assertEquals(EnumSet.of(ReactorAlarm.OVERHEAT), alarms(true, false, 2500, 0, 1000, 0, 4000));
        assertEquals(EnumSet.of(ReactorAlarm.OVERHEAT), alarms(false, true, 2000, 0, 1000, 0, 4000),
                "a hot reactor alarms even when off");
        assertEquals(EnumSet.of(ReactorAlarm.COOLANT_LOW), alarms(true, true, 500, 0.05, 1000, 0, 4000));
        assertEquals(EnumSet.noneOf(ReactorAlarm.class), alarms(false, true, 20, 0, 1000, 0, 4000),
                "an idle reactor needs no coolant");
        assertEquals(EnumSet.noneOf(ReactorAlarm.class), alarms(true, false, 500, 0, 1000, 0, 4000),
                "a passively-cooled reactor has no coolant tank");
        assertEquals(EnumSet.of(ReactorAlarm.NO_FUEL), alarms(true, false, 20, 0, 0, 0, 4000));
        assertEquals(EnumSet.of(ReactorAlarm.WASTE_FULL), alarms(true, false, 20, 0, 300, 3600, 4000));

        assertTrue(ReactorAlarm.anyCritical(EnumSet.of(ReactorAlarm.NO_FUEL, ReactorAlarm.COOLANT_LOW)));
        assertFalse(ReactorAlarm.anyCritical(EnumSet.of(ReactorAlarm.NO_FUEL, ReactorAlarm.WASTE_FULL)));
    }

    @Test
    void statusSyncFiresOnNoticeableChangesAtMostEveryInterval() {

        final StatusSyncTracker tracker = StatusSyncTracker.forReactor();

        assertTrue(reactor(tracker, 20, 1), "the first check sends the state");

        int sent = 0;

        for (int tick = 0; tick < 200; ++tick) {
            sent += reactor(tracker, 20 + tick * 0.01, 1) ? 1 : 0;
        }

        assertEquals(0, sent, "a steady reactor sends nothing");

        for (int tick = 0; tick < StatusSyncTracker.MIN_INTERVAL * 10; ++tick) {
            sent += reactor(tracker, 20 + tick * 10, 1) ? 1 : 0;
        }

        assertTrue(sent > 0 && sent <= 10, "a heating reactor sends at most once per interval: " + sent);

        final StatusSyncTracker coolant = StatusSyncTracker.forReactor();

        reactor(coolant, 500, 1);
        skipInterval(coolant, () -> reactor(coolant, 500, 1));
        assertTrue(reactor(coolant, 500, 0.9), "a coolant drop is sent");
    }

    @Test
    void statusSyncAlwaysSendsThresholdCrossings() {

        final StatusSyncTracker overheat = StatusSyncTracker.forReactor();

        reactor(overheat, 1950, 0.5);
        skipInterval(overheat, () -> reactor(overheat, 1990, 0.5));
        assertFalse(reactor(overheat, 1990, 0.5), "a small step under the overheat threshold is not sent");
        assertTrue(reactor(overheat, EffectThresholds.OVERHEAT_TEMPERATURE + 1, 0.5),
                "a small step over the overheat threshold is sent");
        skipInterval(overheat, () -> reactor(overheat, 2001, 0.5));
        assertTrue(reactor(overheat, 1999, 0.5), "and so is falling back under it");

        final StatusSyncTracker coolant = StatusSyncTracker.forReactor();

        reactor(coolant, 500, 0.12);
        skipInterval(coolant, () -> reactor(coolant, 500, 0.12));
        assertTrue(reactor(coolant, 500, 0.09), "dropping under the low-coolant share is sent");

        final StatusSyncTracker steam = StatusSyncTracker.forReactor();

        reactor(steam, 95, 0.5);
        skipInterval(steam, () -> reactor(steam, 95, 0.5));
        assertTrue(reactor(steam, EffectThresholds.STEAM_TEMPERATURE, 0.5), "reaching steam temperature is sent");

        final StatusSyncTracker fuel = StatusSyncTracker.forReactor();

        assertTrue(fuel.shouldSync(500, 0.5, 0, 1, 0.5));
        skipInterval(fuel, () -> fuel.shouldSync(500, 0.5, 0, 1, 0.5));
        assertTrue(fuel.shouldSync(500, 0.5, 0, 0, 0.5), "running out of fuel is sent");
        skipInterval(fuel, () -> fuel.shouldSync(500, 0.5, 0, 0, 0.5));
        assertTrue(fuel.shouldSync(500, 0.5, 0, 0, 0.91), "filling up with waste is sent");

        final StatusSyncTracker duringInterval = StatusSyncTracker.forReactor();

        reactor(duringInterval, 1990, 0.5);
        skipInterval(duringInterval, () -> reactor(duringInterval, 2010, 0.5));
        assertTrue(reactor(duringInterval, 2010, 0.5), "a crossing during the interval is sent after it");
    }

    private static boolean reactor(final StatusSyncTracker tracker, final double temperature, final double coolant) {
        return tracker.shouldSync(temperature, coolant, 0, 1000, 0);
    }

    private static void skipInterval(final StatusSyncTracker tracker, final BooleanSupplier check) {
        for (int tick = 0; tick < StatusSyncTracker.MIN_INTERVAL; ++tick) {
            assertFalse(check.getAsBoolean(), "nothing is sent within the interval");
        }
    }

    private static Set<ReactorAlarm> alarms(final boolean active, final boolean activelyCooled,
                                            final double temperature, final double coolant,
                                            final int fuel, final int waste, final int capacity) {
        return ReactorAlarm.evaluate(new ReactorAlarm.Readings(active, activelyCooled, temperature, coolant, fuel,
                waste, capacity));
    }
}
